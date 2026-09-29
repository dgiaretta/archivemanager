package info.oais.archive.manager;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.format.FormatDescriptionRdfService;
import info.oais.archive.manager.service.format.RepInfoGroupMigration;
import info.oais.infomodel.structure.description.PrimitiveType;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Representation Information saved as groups: an AND group of the Semantic
 * Representation Information and an OR group of the structure descriptions,
 * each alternative an AND group with the software that applies it -- and the
 * conversion of descriptions saved before groups existed. Everything created
 * is removed again afterwards (the store is shared across test runs), except
 * the shared software individuals, which are meant to persist.
 */
@SpringBootTest
class RepInfoGroupsTest {

    @Autowired
    private FormatDescriptionRdfService rdfService;
    @Autowired
    private RepInfoGroupMigration migration;
    @Autowired
    private EditService edit;
    @Autowired
    private RdfStore store;
    @Autowired
    private QueryRunner q;

    private static FormatDefinition format() {
        FormatDefinition def = new FormatDefinition();
        def.setName("groups test format");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        TestFormats.addField(def, "t", PrimitiveType.FLOAT32, null, null, "Temperature", null, "K");
        return def;
    }

    @Test
    void savesAlternativeDescriptionsAsAnOrGroupInsideAnAndGroup() {
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("DFDL", "<schema/>");
        saved.put("Kaitai Struct, written by hand", "meta: {id: x}");
        String dataObject = write(() -> rdfService.saveToArchive(format(), null, saved));
        try {
            read(() -> {
                assertGrouped(dataObject, 2);
                List<Map<String, String>> software = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?software ?label WHERE {
                          <%s> im:interpretedUsing/im:hasStructureRepresentationInformation/im:hasGroupMember ?alt .
                          ?alt a im:RepInfoAndGroup ; im:hasOtherRepresentationInformation ?software .
                          ?software a im:OtherRepresentationInformation ; rdfs:label ?label .
                        }""".formatted(dataObject));
                assertThat(software).extracting(r -> r.get("label"))
                        .containsExactlyInAnyOrder("Apache Daffodil 3.11", "Kaitai Struct compiler 0.11");
                assertThat(software).extracting(r -> r.get("software"))
                        .containsExactlyInAnyOrder(Ns.EX + "software-dfdl", Ns.EX + "software-kaitai");
                return null;
            });
        } finally {
            removeReachable(dataObject);
        }
    }

    @Test
    void convertsDescriptionsSavedOneRepresentationInformationPerFormat() {
        String[] iris = write(() -> {
            String dataObject = edit.createEntity(Ns.IM + "DigitalObject");
            String semantic = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
            for (String format : List.of("DFDL", "DRB SDF schema (Java, fr.gael.drb)")) {
                String structure = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
                edit.addLiteral(structure, Ns.RDFS + "comment", "layout");
                String repInfo = edit.createEntity(Ns.IM + "RepresentationInformation");
                edit.addLiteral(repInfo, Ns.RDFS + "comment", "\"old format\" interpreted via its " + format + " description.");
                edit.addRelationship(repInfo, Ns.IM + "hasStructureRepresentationInformation", structure);
                edit.addRelationship(repInfo, Ns.IM + "hasSemanticRepresentationInformation", semantic);
                edit.addRelationship(dataObject, Ns.IM + "interpretedUsing", repInfo);
            }
            // One whose old Representation Information has since had something else attached: left alone.
            String touched = edit.createEntity(Ns.IM + "DigitalObject");
            String touchedRi = edit.createEntity(Ns.IM + "RepresentationInformation");
            edit.addLiteral(touchedRi, Ns.RDFS + "comment", "\"touched\" interpreted via its DFDL description.");
            edit.addRelationship(touchedRi, Ns.IM + "hasStructureRepresentationInformation",
                    edit.createEntity(Ns.IM + "StructureRepresentationInformation"));
            edit.addRelationship(touchedRi, Ns.IM + "hasSemanticRepresentationInformation",
                    edit.createEntity(Ns.IM + "SemanticRepresentationInformation"));
            edit.addLiteral(touchedRi, Ns.SKOS + "note", "added by hand");
            edit.addRelationship(touched, Ns.IM + "interpretedUsing", touchedRi);
            return new String[] {dataObject, touched, touchedRi};
        });
        try {
            assertThat(write(() -> migration.migrate())).isEqualTo(1);
            read(() -> {
                assertGrouped(iris[0], 2);
                assertThat(q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?ri WHERE { <%s> im:interpretedUsing ?ri }""".formatted(iris[1])))
                        .extracting(r -> r.get("ri")).containsExactly(iris[2]);
                return null;
            });
            assertThat(write(() -> migration.migrate())).as("nothing left to convert").isZero();
        } finally {
            removeReachable(iris[0]);
            removeReachable(iris[1]);
        }
    }

    /** The Data Object is interpreted using one AND group of its semantics and an OR group of n alternatives. */
    private void assertGrouped(String dataObject, int alternatives) {
        List<Map<String, String>> top = q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT ?top ?or ?semantic WHERE {
                  <%s> im:interpretedUsing ?top .
                  ?top a im:RepInfoAndGroup , im:RepresentationInformation ;
                       im:hasGroupMember ?or , ?semantic ;
                       im:hasStructureRepresentationInformation ?or ;
                       im:hasSemanticRepresentationInformation ?semantic .
                  ?or a im:RepInfoOrGroup , im:StructureRepresentationInformation .
                  ?semantic a im:SemanticRepresentationInformation .
                }""".formatted(dataObject));
        assertThat(top).hasSize(1);
        List<Map<String, String>> members = q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT ?alt ?structure WHERE {
                  <%s> im:hasGroupMember ?alt .
                  ?alt a im:RepInfoAndGroup ; im:hasGroupMember ?structure .
                  ?structure a im:StructureRepresentationInformation .
                  FILTER (?structure != <%s>)
                }""".formatted(top.get(0).get("or"), top.get(0).get("or")));
        assertThat(members).hasSize(alternatives);
    }

    private <T> T write(Supplier<T> work) {
        store.beginTransaction(ReadWrite.WRITE);
        boolean ok = false;
        try {
            T result = work.get();
            ok = true;
            return result;
        } finally {
            store.endTransaction(ok);
        }
    }

    private void read(Supplier<Void> work) {
        store.beginTransaction(ReadWrite.READ);
        try {
            work.get();
        } finally {
            store.endTransaction(true);
        }
    }

    /** Removes {@code start} and everything reachable from it, except the shared software individuals. */
    private void removeReachable(String start) {
        write(() -> {
            Model m = store.dataModel();
            Set<String> seen = new LinkedHashSet<>();
            Deque<String> todo = new ArrayDeque<>(List.of(start));
            while (!todo.isEmpty()) {
                String iri = todo.pop();
                if (!seen.add(iri) || iri.startsWith(Ns.EX + "software-")) {
                    continue;
                }
                for (Statement s : m.listStatements(m.getResource(iri), null, (RDFNode) null).toList()) {
                    if (s.getObject().isURIResource() && s.getObject().asResource().getURI().startsWith(Ns.EX)) {
                        todo.push(s.getObject().asResource().getURI());
                    }
                }
            }
            seen.removeIf(iri -> iri.startsWith(Ns.EX + "software-"));
            for (String iri : seen) {
                Resource r = m.getResource(iri);
                m.removeAll(r, null, null);
                m.removeAll(null, null, r);
            }
            return null;
        });
    }
}
