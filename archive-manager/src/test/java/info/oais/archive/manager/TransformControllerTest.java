package info.oais.archive.manager;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.security.EditAuthInterceptor;
import info.oais.archive.manager.service.AipComponents;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.format.DataObjectViewService;
import info.oais.infomodel.structure.StructureNode;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransformControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RdfStore store;
    @Autowired
    private EditService edit;
    @Autowired
    private ArchiveService archive;
    @Autowired
    private BitStore bits;
    @Autowired
    private DataObjectViewService views;
    @Autowired
    private AipComponents components;

    @Test
    void transformsAStarCatalogueIntoPositionsAndRecordsIt() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        BitStore.StoredBits sourceBits = bits.store(TransformFixtures.catalogue(), "stars.bin");
        Path newBits = null;
        try {
            Map<String, String> made = write(() -> catalogueAndTarget("http://localhost" + sourceBits.path()));
            String id = archive.encodeId(made.get("source"));
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);

            mockMvc.perform(get("/resource/" + id)).andExpect(content().string(containsString("Transform&hellip;")));
            mockMvc.perform(get("/transform/" + id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Test star positions (as used by Test positions)")));
            mockMvc.perform(get("/transform/" + id).param("target", made.get("targetRepInfo")).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("for star in star")))
                    .andExpect(content().string(containsString("star.dec = star.dec")))
                    .andExpect(content().string(containsString("star.name = star.name")));

            mockMvc.perform(form(post("/transform/" + id + "/try"), made).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("not shown reversible")))
                    .andExpect(content().string(containsString("star.vmag: not carried over")))
                    .andExpect(content().string(containsString("units differ")));
            mockMvc.perform(get("/transform/" + id + "/trial").session(session)).andExpect(status().isOk());

            String location = mockMvc.perform(form(post("/transform/" + id), made).session(session))
                    .andExpect(redirectedUrlPattern("/resource/*"))
                    .andReturn().getResponse().getRedirectedUrl();
            String transformation = archive.decodeId(location.substring("/resource/".length()));

            String newObject = read(() -> {
                Model m = store.dataModel();
                Resource t = m.getResource(transformation);
                assertThat(types(t)).contains("Transformation", "NonReversibleTransformation");
                assertThat(object(t, "transformationSource")).isEqualTo(made.get("source"));
                assertThat(t.getProperty(m.createProperty(Ns.IM + "performedAt")).getLiteral().getDatatypeURI())
                        .isEqualTo(XSDDatatype.XSDdateTime.getURI());
                Resource mapping = m.getResource(object(t, "followedMapping"));
                assertThat(literal(mapping, Ns.IM + "specificationText"))
                        .contains("star.ra_rad = star.ra * 0.017453292519943295");

                Map<String, String> outcomes = t.listProperties(m.createProperty(Ns.IM + "hasPropertyCheck")).toList()
                        .stream().map(s -> s.getObject().asResource()).collect(Collectors.toMap(
                                c -> literal(c, Ns.IM + "sourcePath"), c -> literal(c, Ns.IM + "checkOutcome")));
                assertThat(outcomes).containsExactlyInAnyOrderEntriesOf(Map.of("star.dec", "preserved",
                        "star.ra", "not checked", "star.vmag", "changed"));

                Resource result = m.getResource(object(t, "transformationResult"));
                assertThat(object(result, "interpretedUsing")).isEqualTo(made.get("targetRepInfo"));
                assertThat(object(result, "hasStorageLocation")).startsWith("http://localhost/api/bits/")
                        .endsWith("/Test-bright-stars.pos");

                Resource content = m.listSubjectsWithProperty(m.createProperty(Ns.IM + "hasDataObject"), result)
                        .next();
                Resource aip = m.listSubjectsWithProperty(m.createProperty(Ns.IM + "hasContentInformation"), content)
                        .next();
                assertThat(types(aip)).contains("ArchivalInformationPackage", "AIPVersion");
                assertThat(object(aip, "hasSourceAIP")).isEqualTo(made.get("sourceAip"));
                Resource pdi = m.getResource(object(aip, "hasPreservationDescriptiveInformation"));
                assertThat(literal(m.getResource(object(pdi, "hasFixityInformation")), Ns.RDFS + "comment"))
                        .startsWith("SHA-256: ");
                assertThat(object(m.getResource(object(pdi, "hasProvenanceInformation")), "recordsTransformation"))
                        .isEqualTo(transformation);

                // Reference, Context and Access Rights Information are copies of the source AIP's.
                for (String kind : List.of("Reference", "Context", "AccessRights")) {
                    String copy = object(pdi, "has" + kind + "Information");
                    assertThat(copy).isNotNull().isNotEqualTo(made.get("source" + kind));
                    assertThat(comments(m.getResource(copy))).contains("Source " + kind + ".")
                            .anyMatch(c -> c.startsWith("Carried over from ") && c.contains(made.get("source" + kind)));
                }
                assertThat(comments(m.getResource(object(pdi, "hasReferenceInformation"))))
                        .contains("Identifier of this AIP Version's Content Data Object: " + result.getURI());
                assertThat(comments(m.getResource(object(pdi, "hasContextInformation"))))
                        .anyMatch(c -> c.startsWith("Made by a Transformation from Test bright stars"));
                Resource description = m.getResource(object(aip, "describedBy"));
                assertThat(object(description, "derivedFrom")).isEqualTo(aip.getURI());
                assertThat(literal(description, Ns.RDFS + "comment")).contains("an AIP Version made by a Transformation "
                        + "of Test bright stars").contains("into Test star positions, on ")
                        .contains("The source package is described as: The bright star catalogue.");
                assertThat(AipComponents.complete(components.check(aip.getURI(), Map.of(), null))).isTrue();
                assertThat(content.listProperties(m.createProperty(Ns.IM + "hasTransformationInformationProperty"))
                        .toList()).hasSize(2);

                List<Statement> sourceProperties = m.getResource(made.get("sourceContent"))
                        .listProperties(m.createProperty(Ns.IM + "hasTransformationInformationProperty")).toList();
                assertThat(sourceProperties).hasSize(3);
                assertThat(sourceProperties.stream().map(s -> object(s.getObject().asResource(),
                        "dependsOnRepresentationInformation"))).contains(made.get("decMeaning"));
                return result.getURI();
            });
            newBits = read(() -> bits.file(URI.create(object(store.dataModel().getResource(newObject),
                    "hasStorageLocation")))).orElseThrow();

            // The new Data Object's bits are read from the archive's own store, and decode with its own description.
            int stars = read(() -> {
                try {
                    return views.decode(newObject, (data, root) -> StructurePathsForTest.count(root, "star"));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            assertThat(stars).isEqualTo(3);
            String path = newBits.getParent().getFileName() + "/" + newBits.getFileName();
            mockMvc.perform(get("/api/bits/" + path)).andExpect(status().isOk());

            // Done again, the Transformation Information Properties the first one added are reused.
            mockMvc.perform(form(post("/transform/" + id), made).session(session))
                    .andExpect(redirectedUrlPattern("/resource/*"));
            assertThat(read(() -> store.dataModel().getResource(made.get("sourceContent"))
                    .listProperties(store.dataModel().createProperty(Ns.IM + "hasTransformationInformationProperty"))
                    .toList())).hasSize(3);
        } finally {
            cleanUp(before);
            bits.delete(sourceBits);
        }
    }

    @Test
    void recordsATransformationDoneWithAnotherApplication() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        BitStore.StoredBits sourceBits = bits.store(TransformFixtures.catalogue(), "stars.bin");
        try {
            Map<String, String> made = write(() -> catalogueAndTarget("http://localhost" + sourceBits.path()));
            String id = archive.encodeId(made.get("source"));
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);

            mockMvc.perform(get("/transform/" + id).session(session))
                    .andExpect(content().string(containsString("Or transform it with another application")));
            assertThat(mockMvc.perform(get("/api/data-objects/" + id + "/bits")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray()).isEqualTo(TransformFixtures.catalogue());

            // What the other application made: the catalogue as positions, right ascension in radians.
            java.io.ByteArrayOutputStream positions = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(positions)) {
                out.writeInt(TransformFixtures.CATALOGUE.length);
                for (Object[] star : TransformFixtures.CATALOGUE) {
                    out.write(String.format("%-12s", star[0]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    out.writeDouble(Math.toRadians((Double) star[1]));
                    out.writeFloat(((Double) star[2]).floatValue());
                }
            }
            org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                    "file", "positions.pos", "application/octet-stream", positions.toByteArray());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/transform/" + id + "/external").file(file).session(session)
                            .param("application", "Astropy").param("performedBy", "A. Archivist"))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Say what was done.")));

            String location = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/transform/" + id + "/external").file(file).session(session)
                            .param("application", "Astropy").param("applicationVersion", "6.1")
                            .param("applicationUrl", "https://www.astropy.org/")
                            .param("performedBy", "A. Archivist").param("performedOn", "2026-09-30")
                            .param("method", "Read with astropy.table, converted ra to radians, wrote positions.")
                            .param("repInfo", made.get("targetRepInfo"))
                            .param("reversibility", "magnitudes left out")
                            .param("ext.tip.star.dec", "on").param("ext.target.star.dec", "star.dec")
                            .param("ext.outcome.star.dec", "changed")
                            .param("ext.tip.star.vmag", "on").param("ext.outcome.star.vmag", "changed")
                            .param("ext.evidence.star.vmag", "magnitudes are not in the new format"))
                    .andExpect(redirectedUrlPattern("/resource/*")).andReturn().getResponse().getRedirectedUrl();
            String transformation = archive.decodeId(location.substring("/resource/".length()));

            read(() -> {
                Model m = store.dataModel();
                Resource t = m.getResource(transformation);
                assertThat(types(t)).contains("Transformation", "NonReversibleTransformation");
                assertThat(t.getProperty(m.createProperty(Ns.IM + "performedAt")).getLiteral().getLexicalForm())
                        .isEqualTo("2026-09-30");
                assertThat(comments(t)).contains("Method: Read with astropy.table, converted ra to radians, wrote "
                        + "positions.", "Not shown reversible: magnitudes left out");
                assertThat(t.listProperties(m.createProperty(Ns.IM + "performedBy")).toList().stream()
                        .map(st -> literal(st.getObject().asResource(), Ns.RDFS + "label")))
                        .containsExactlyInAnyOrder("A. Archivist", "Astropy 6.1");
                Map<String, Resource> checks = t.listProperties(m.createProperty(Ns.IM + "hasPropertyCheck")).toList()
                        .stream().map(st -> st.getObject().asResource()).collect(Collectors.toMap(
                                c -> literal(c, Ns.IM + "sourcePath"), c -> c));
                // The archive checked the declination itself, whatever was reported...
                assertThat(literal(checks.get("star.dec"), Ns.IM + "checkOutcome")).isEqualTo("preserved");
                assertThat(comments(checks.get("star.dec"))).anyMatch(c -> c.contains("Checked by the archive"));
                // ...and recorded what was reported for the magnitudes, which it couldn't check.
                assertThat(literal(checks.get("star.vmag"), Ns.IM + "checkOutcome")).isEqualTo("changed");
                assertThat(comments(checks.get("star.vmag")))
                        .contains("Reported by A. Archivist: magnitudes are not in the new format");

                Resource result = m.getResource(object(t, "transformationResult"));
                assertThat(object(result, "interpretedUsing")).isEqualTo(made.get("targetRepInfo"));
                assertThat(object(result, "hasStorageLocation")).endsWith("/positions.pos");
                Resource content = m.listSubjectsWithProperty(m.createProperty(Ns.IM + "hasDataObject"), result)
                        .next();
                Resource aip = m.listSubjectsWithProperty(m.createProperty(Ns.IM + "hasContentInformation"), content)
                        .next();
                Resource pdi = m.getResource(object(aip, "hasPreservationDescriptiveInformation"));
                assertThat(literal(m.getResource(object(pdi, "hasProvenanceInformation")), Ns.RDFS + "comment"))
                        .contains("done outside the archive with Astropy 6.1 by A. Archivist on 2026-09-30");
                assertThat(AipComponents.complete(components.check(aip.getURI(), Map.of(), null))).isTrue();
                return null;
            });
        } finally {
            cleanUp(before);
            bits.delete(sourceBits);
        }
    }

    /** Removes everything made since {@code before}, and the bits stored for it. */
    private void cleanUp(Set<Resource> before) {
        write(() -> {
            Model m = store.dataModel();
            for (Resource r : m.listSubjects().toList()) {
                if (!before.contains(r)) {
                    for (Statement s : m.listStatements(r, m.createProperty(Ns.IM + "hasStorageLocation"),
                            (RDFNode) null).toList()) {
                        bits.file(URI.create(s.getObject().asResource().getURI())).ifPresent(this::deleteStored);
                    }
                    m.removeAll(r, null, null);
                    m.removeAll(null, null, r);
                }
            }
            return null;
        });
    }

    /** The source catalogue, with what two of its elements mean, in an AIP; and a Data Object in the target format. */
    private Map<String, String> catalogueAndTarget(String sourceLocation) {
        String source = edit.createEntity(Ns.IM + "DigitalObject");
        edit.addLiteral(source, Ns.RDFS + "label", "Test bright stars");
        edit.addRelationship(source, Ns.IM + "hasStorageLocation", sourceLocation);
        String top = repInfo(source, TransformFixtures.STARS, "Test bright star catalogue");
        String decMeaning = meaning(top, "star.dec", "Declination", "deg");
        meaning(top, "star.ra", "Right ascension", "deg");
        String content = edit.createEntity(Ns.IM + "ContentInformation");
        edit.addRelationship(content, Ns.IM + "hasDataObject", source);
        String aip = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
        edit.addRelationship(aip, Ns.IM + "hasContentInformation", content);
        String pdi = edit.createEntity(Ns.IM + "PreservationDescriptionInformation");
        edit.addRelationship(aip, Ns.IM + "hasPreservationDescriptiveInformation", pdi);
        Map<String, String> made = new java.util.HashMap<>();
        for (String kind : List.of("Reference", "Context", "AccessRights")) {
            String part = edit.createEntity(Ns.IM + kind + "Information");
            edit.addLiteral(part, Ns.RDFS + "comment", "Source " + kind + ".");
            edit.addRelationship(pdi, Ns.IM + "has" + kind + "Information", part);
            made.put("source" + kind, part);
        }
        String description = edit.createEntity(Ns.IM + "PackageDescription");
        edit.addLiteral(description, Ns.RDFS + "comment", "The bright star catalogue.");
        edit.addRelationship(aip, Ns.IM + "describedBy", description);

        String target = edit.createEntity(Ns.IM + "DigitalObject");
        edit.addLiteral(target, Ns.RDFS + "label", "Test positions");
        edit.addRelationship(target, Ns.IM + "hasStorageLocation", "https://example.org/positions.pos");
        String targetTop = repInfo(target, TransformFixtures.POSITIONS, "Test star positions");
        meaning(targetTop, "star.ra_rad", "Right ascension", "rad");
        meaning(targetTop, "star.dec", "Declination", "deg");
        made.putAll(Map.of("source", source, "sourceContent", content, "sourceAip", aip, "decMeaning", decMeaning,
                "targetRepInfo", targetTop));
        return made;
    }

    private String repInfo(String dataObject, String dfdl, String label) {
        String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
        edit.addLiteral(top, Ns.RDFS + "label", label);
        edit.addRelationship(dataObject, Ns.IM + "interpretedUsing", top);
        String structure = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
        edit.addLiteral(structure, Ns.IM + "specificationLanguage", "DFDL");
        edit.addLiteral(structure, Ns.IM + "specificationText", dfdl);
        edit.addRelationship(top, Ns.IM + "hasGroupMember", structure);
        edit.addRelationship(top, Ns.IM + "hasStructureRepresentationInformation", structure);
        String semantics = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addRelationship(top, Ns.IM + "hasGroupMember", semantics);
        edit.addRelationship(top, Ns.IM + "hasSemanticRepresentationInformation", semantics);
        return top;
    }

    private String meaning(String top, String path, String label, String unit) {
        Model m = store.dataModel();
        String semantics = object(m.getResource(top), "hasSemanticRepresentationInformation");
        String ri = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addLiteral(ri, Ns.RDFS + "label", label);
        edit.addLiteral(ri, Ns.IM + "structuralPath", path);
        String unitIri = edit.createEntity(Ns.IM + "UnitOfMeasurement");
        edit.addLiteral(unitIri, Ns.RDFS + "label", unit);
        edit.addRelationship(ri, Ns.IM + "hasUnitOfMeasurement", unitIri);
        edit.addRelationship(semantics, Ns.IM + "interpretedUsingRecurse", ri);
        return ri;
    }

    private MockHttpServletRequestBuilder form(MockHttpServletRequestBuilder request, Map<String, String> made) {
        return request.param("target", made.get("targetRepInfo"))
                .param("mappingText", TransformFixtures.MAPPING).param("useText", "on")
                .param("tip.star.dec", "on").param("tip.star.ra", "on").param("tip.star.vmag", "on");
    }

    private void deleteStored(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Set<String> types(Resource r) {
        return r.listProperties(RDF.type).toList().stream()
                .map(s -> s.getObject().asResource().getURI().substring(Ns.IM.length())).collect(Collectors.toSet());
    }

    private static String object(Resource r, String imProperty) {
        Statement s = r.getProperty(r.getModel().createProperty(Ns.IM + imProperty));
        return s == null ? null : s.getObject().asResource().getURI();
    }

    private static List<String> comments(Resource r) {
        return r.listProperties(r.getModel().createProperty(Ns.RDFS + "comment")).toList().stream()
                .map(Statement::getString).toList();
    }

    private static String literal(Resource r, String property) {
        Statement s = r.getProperty(r.getModel().createProperty(property));
        return s == null ? null : s.getString();
    }

    private <T> T read(Supplier<T> work) {
        store.beginTransaction(ReadWrite.READ);
        try {
            return work.get();
        } finally {
            store.endTransaction(true);
        }
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

    /** Counts a decoded tree's top-level elements called {@code name}. */
    private static final class StructurePathsForTest {
        static int count(StructureNode root, String name) {
            return (int) root.getChildren().stream().filter(c -> c.getName().equals(name)).count();
        }
    }
}
