package info.oais.archive.manager.service.format;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.EditService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Representation Information that RepInfo Tools saved before groups
 * existed into groups (see {@link FormatDescriptionRdfService}). The old
 * form was one {@code im:RepresentationInformation} per saved format, each
 * with one Structure and the shared Semantic Representation Information,
 * all {@code interpretedUsing} from the same Data Object, and commented
 * {@code "<name>" interpreted via its <format> description.} Each such set
 * becomes an AND group of the Semantic Representation Information and an OR
 * group of the structure descriptions; the old RepresentationInformation
 * individuals are removed, and the Structure and Semantic Representation
 * Information themselves are kept and reused.
 *
 * <p>Runs once at startup, and finds nothing to do after that. A set is left
 * as it is (with a warning in the log) if anything other than RepInfo Tools
 * has since been attached to one of its old RepresentationInformation
 * individuals, since removing them would lose it.</p>
 */
@Component
public class RepInfoGroupMigration {

    private static final Logger log = LoggerFactory.getLogger(RepInfoGroupMigration.class);
    private static final Pattern OLD_COMMENT = Pattern.compile("(?s)\"(.*)\" interpreted via its (.*) description\\.");

    private final RdfStore store;
    private final QueryRunner q;
    private final EditService edit;
    private final FormatDescriptionRdfService rdf;

    public RepInfoGroupMigration(RdfStore store, QueryRunner q, EditService edit, FormatDescriptionRdfService rdf) {
        this.store = store;
        this.q = q;
        this.edit = edit;
        this.rdf = rdf;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        boolean success = false;
        store.beginTransaction(ReadWrite.WRITE);
        try {
            int converted = migrate();
            if (converted > 0) {
                log.info("Converted the Representation Information of {} Data Object(s) into groups", converted);
            }
            success = true;
        } finally {
            store.endTransaction(success);
        }
    }

    private record Old(String repInfo, String structure, String formatLabel, String name) {
    }

    /**
     * Converts every set of old-style Representation Information in the data graph.
     *
     * @return how many sets were converted
     */
    public int migrate() {
        List<Map<String, String>> rows = q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT ?dataObject ?repInfo ?structure ?semantic ?comment WHERE {
                  ?dataObject im:interpretedUsing ?repInfo .
                  ?repInfo rdf:type im:RepresentationInformation ;
                           rdfs:comment ?comment ;
                           im:hasStructureRepresentationInformation ?structure ;
                           im:hasSemanticRepresentationInformation ?semantic .
                  FILTER NOT EXISTS { ?repInfo rdf:type im:RepInfoGroup }
                } ORDER BY ?dataObject ?repInfo""");
        Map<List<String>, List<Old>> sets = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            Matcher m = OLD_COMMENT.matcher(row.get("comment"));
            if (m.matches()) {
                sets.computeIfAbsent(List.of(row.get("dataObject"), row.get("semantic")), k -> new ArrayList<>())
                        .add(new Old(row.get("repInfo"), row.get("structure"), m.group(2), m.group(1)));
            }
        }
        int converted = 0;
        for (Map.Entry<List<String>, List<Old>> set : sets.entrySet()) {
            String dataObject = set.getKey().get(0);
            String semantic = set.getKey().get(1);
            List<Old> olds = set.getValue();
            if (olds.stream().anyMatch(old -> !onlyWhatRepInfoToolsWrote(old.repInfo(), dataObject))) {
                log.warn("Not converting the Representation Information of {} into groups: something else has "
                        + "been attached to it since it was saved", dataObject);
                continue;
            }
            String name = olds.get(0).name();
            Map<String, String> structures = new LinkedHashMap<>();
            boolean applied = false;
            for (Old old : olds) {
                structures.put(old.formatLabel(), old.structure());
                applied |= appliedBySoftware(old);
                addLabelIfMissing(old.structure(), name + ": " + old.formatLabel() + " description");
            }
            addLabelIfMissing(semantic, "Semantics of " + name);
            rdf.linkAsGroups(dataObject, name, semantic, structures, applied);
            olds.forEach(old -> edit.deleteResource(old.repInfo()));
            converted++;
        }
        return converted;
    }

    /** Whether {@code repInfo} has only the triples RepInfo Tools gave it. */
    private boolean onlyWhatRepInfoToolsWrote(String repInfo, String dataObject) {
        Model m = store.dataModel();
        Resource r = m.getResource(repInfo);
        Set<String> expected = Set.of(RDF.type.getURI(), Ns.RDFS + "comment",
                Ns.IM + "hasStructureRepresentationInformation", Ns.IM + "hasSemanticRepresentationInformation");
        for (Statement s : m.listStatements(r, null, (RDFNode) null).toList()) {
            if (!expected.contains(s.getPredicate().getURI())) {
                return false;
            }
        }
        for (Statement s : m.listStatements(null, null, r).toList()) {
            if (!s.getSubject().getURI().equals(dataObject)
                    || !s.getPredicate().getURI().equals(Ns.IM + "interpretedUsing")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an old description is applied by software (a byte layout's) rather than documentation (a
     * logical tree's DRB outputs). Kaitai, DFDL and SDF schemas only exist for byte layouts; a drb-python
     * driver, as opposed to a logical tree's documented scaffold, defines its factory from a DESCRIPTION.
     */
    private boolean appliedBySoftware(Old old) {
        FormatDescriptionRdfService.Processor processor = FormatDescriptionRdfService.Processor.forLabel(old.formatLabel());
        if (processor == null) {
            return false;
        }
        if (processor != FormatDescriptionRdfService.Processor.DRB_PYTHON) {
            return true;
        }
        Statement comment = store.dataModel().getResource(old.structure()).getProperty(
                store.dataModel().createProperty(Ns.RDFS + "comment"));
        return comment != null && comment.getString().contains("\nDESCRIPTION = ");
    }

    private void addLabelIfMissing(String iri, String label) {
        Model m = store.dataModel();
        if (!m.getResource(iri).hasProperty(m.createProperty(Ns.RDFS + "label"))) {
            edit.addLiteral(iri, Ns.RDFS + "label", label);
        }
    }
}
