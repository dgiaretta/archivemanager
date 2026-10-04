package info.oais.archive.manager;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.EditService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageExportTest {

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

    @Test
    void writesAnAipAsAValidBagAndADataDescription() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        byte[] catalogue = TransformFixtures.catalogue();
        BitStore.StoredBits stored = bits.store(catalogue, "stars.bin");
        try {
            Map<String, String> made = write(() -> aip("http://localhost" + stored.path()));

            mockMvc.perform(get("/resource/" + archive.encodeId(made.get("aip"))))
                    .andExpect(content().string(containsString("Download as BagIt")));
            mockMvc.perform(get("/resource/" + archive.encodeId(made.get("dataObject"))))
                    .andExpect(content().string(containsString("Download the data description")));

            byte[] zip = mockMvc.perform(get("/api/packages/" + archive.encodeId(made.get("aip")) + "/bagit.zip"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            Map<String, byte[]> bag = unzip(zip);
            String root = "Test-star-catalogue-AIP/";
            assertThat(text(bag, root + "bagit.txt")).isEqualTo("BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n");

            // Every payload file is in the manifest with its digest, and nothing else is.
            Map<String, String> manifest = manifest(text(bag, root + "manifest-sha256.txt"));
            Set<String> payload = new HashSet<>();
            long octets = 0;
            for (Map.Entry<String, byte[]> e : bag.entrySet()) {
                if (e.getKey().startsWith(root + "data/")) {
                    String path = e.getKey().substring(root.length());
                    payload.add(path);
                    octets += e.getValue().length;
                    assertThat(manifest.get(path)).as(path).isEqualTo(BitStore.sha256(e.getValue()));
                }
            }
            assertThat(manifest.keySet()).isEqualTo(payload);
            assertThat(text(bag, root + "bag-info.txt")).contains("Payload-Oxum: " + octets + "." + payload.size())
                    .contains("External-Identifier: " + made.get("aip"));
            manifest(text(bag, root + "tagmanifest-sha256.txt")).forEach((path, digest) ->
                    assertThat(BitStore.sha256(bag.get(root + path))).as(path).isEqualTo(digest));

            // aip.ttl describes the whole package, naming the files that came with it relative to itself.
            Model aip = ModelFactory.createDefaultModel();
            String base = "file:///bag/" + root + "data/aip.ttl";
            RDFParser.create().source(new ByteArrayInputStream(bag.get(root + "data/aip.ttl"))).base(base)
                    .lang(Lang.TURTLE).parse(aip);
            Resource dataObject = aip.getResource(made.get("dataObject"));
            List<String> locations = dataObject.listProperties(aip.createProperty(Ns.IM + "hasStorageLocation"))
                    .toList().stream().map(s -> s.getObject().asResource().getURI()).toList();
            assertThat(locations).contains("http://localhost" + stored.path(), "file:///bag/" + root
                    + "data/objects/stars.bin");
            assertThat(bag.get(root + "data/objects/stars.bin")).isEqualTo(catalogue);
            Resource dfdl = aip.getResource(made.get("dfdl"));
            String dfdlFile = dfdl.getProperty(aip.createProperty(Ns.IM + "hasStorageLocation")).getObject()
                    .asResource().getURI().substring("file:///bag/".length());
            assertThat(text(bag, dfdlFile)).isEqualTo(TransformFixtures.STARS);
            assertThat(aip.contains(aip.getResource(made.get("provenance")), null)).isTrue();
            assertThat(aip.contains(aip.getResource(made.get("unit")), null)).isTrue();
            assertThat(bag).containsKey(root + "data/ontologies/oais-im-local-extensions.ttl");

            Map<String, byte[]> description = unzip(mockMvc.perform(get("/api/descriptions/"
                            + archive.encodeId(made.get("dataObject")) + "/description.zip"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            String folder = "Test-stars-description/";
            assertThat(description).containsKeys(folder + "description.ttl", folder + "README.txt",
                    folder + "ontologies/oais_im_schema-sh-v5.ttl");
            Model described = ModelFactory.createDefaultModel();
            RDFParser.create().source(new ByteArrayInputStream(description.get(folder + "description.ttl")))
                    .base("file:///d/" + folder + "description.ttl").lang(Lang.TURTLE).parse(described);
            assertThat(described.contains(described.getResource(made.get("dataObject")),
                    described.createProperty(Ns.IM + "interpretedUsing"))).isTrue();
            assertThat(described.contains(described.getResource(made.get("aip")), null)).isFalse();
            String describedDfdl = described.getResource(made.get("dfdl"))
                    .getProperty(described.createProperty(Ns.IM + "hasStorageLocation")).getObject().asResource()
                    .getURI().substring("file:///d/".length());
            assertThat(text(description, describedDfdl)).isEqualTo(TransformFixtures.STARS);

            // The bag names every component an AIP must have, and what this one lacks.
            String components = text(bag, root + "oais-aip-components.txt");
            assertThat(components).contains("AIP: " + made.get("aip"))
                    .contains("Complete: no -- missing Reference Information, Context Information, Fixity "
                            + "Information, Access Rights Information, Package Description")
                    .contains("Content Information (exactly one): present")
                    .contains("  Content Data Object (exactly one): present\n      " + made.get("dataObject"))
                    .contains("  Bits of the Content Data Object (at least one bit sequence): present")
                    .contains("file: data/objects/stars.bin")
                    .contains("file: " + dfdlFile.substring(root.length()))
                    .contains("  Provenance Information (exactly one): present\n      " + made.get("provenance"))
                    .contains("  Fixity Information (exactly one): missing")
                    .contains("Packaging Information (exactly one): provided by the bag\n    " + made.get("aip")
                            + "#bagit-packaging")
                    .contains("Package Description (at least one): missing");
            assertThat(text(bag, root + "bag-info.txt")).contains("OAIS-AIP-Complete: no\n")
                    .contains("OAIS-AIP-Missing: Reference Information, Context Information, Fixity Information, "
                            + "Access Rights Information, Package Description\n");
            assertThat(text(bag, root + "tagmanifest-sha256.txt")).contains("  oais-aip-components.txt\n");
            Resource packaging = aip.getResource(made.get("aip") + "#bagit-packaging");
            assertThat(aip.contains(aip.getResource(made.get("aip")), aip.createProperty(Ns.IM + "delimitedBy"),
                    packaging)).isTrue();
            mockMvc.perform(get("/resource/" + archive.encodeId(made.get("aip"))))
                    .andExpect(content().string(containsString("This AIP is missing components OAIS requires.")));

            write(() -> {
                String pdi = store.dataModel().getResource(made.get("aip"))
                        .getPropertyResourceValue(store.dataModel().createProperty(Ns.IM
                                + "hasPreservationDescriptiveInformation")).getURI();
                for (String kind : List.of("Reference", "Context", "Fixity", "AccessRights")) {
                    String part = edit.createEntity(Ns.IM + kind + "Information");
                    edit.addRelationship(pdi, Ns.IM + "has" + kind + "Information", part);
                }
                String packageDescription = edit.createEntity(Ns.IM + "PackageDescription");
                edit.addRelationship(made.get("aip"), Ns.IM + "describedBy", packageDescription);
                return null;
            });
            Map<String, byte[]> completeBag = unzip(mockMvc.perform(get("/api/packages/"
                    + archive.encodeId(made.get("aip")) + "/bagit.zip")).andReturn().getResponse()
                    .getContentAsByteArray());
            assertThat(text(completeBag, root + "bag-info.txt")).contains("OAIS-AIP-Complete: yes\n")
                    .doesNotContain("OAIS-AIP-Missing");
            assertThat(text(completeBag, root + "oais-aip-components.txt")).contains("Complete: yes\n");
            mockMvc.perform(get("/resource/" + archive.encodeId(made.get("aip"))))
                    .andExpect(content().string(containsString("This AIP has every component OAIS requires.")));

            mockMvc.perform(get("/api/packages/" + archive.encodeId(made.get("dataObject")) + "/bagit.zip"))
                    .andExpect(status().isNotFound());
        } finally {
            write(() -> {
                Model m = store.dataModel();
                for (Resource r : m.listSubjects().toList()) {
                    if (!before.contains(r)) {
                        m.removeAll(r, null, null);
                        m.removeAll(null, null, r);
                    }
                }
                return null;
            });
            bits.delete(stored);
        }
    }

    private Map<String, String> aip(String location) {
        String dataObject = edit.createEntity(Ns.IM + "DigitalObject");
        edit.addLiteral(dataObject, Ns.RDFS + "label", "Test stars");
        edit.addRelationship(dataObject, Ns.IM + "hasStorageLocation", location);
        String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
        edit.addRelationship(dataObject, Ns.IM + "interpretedUsing", top);
        String dfdl = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
        edit.addLiteral(dfdl, Ns.RDFS + "label", "Star catalogue DFDL");
        edit.addLiteral(dfdl, Ns.IM + "specificationLanguage", "DFDL");
        edit.addLiteral(dfdl, Ns.IM + "specificationText", TransformFixtures.STARS);
        edit.addRelationship(top, Ns.IM + "hasGroupMember", dfdl);
        String semantics = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addRelationship(top, Ns.IM + "hasGroupMember", semantics);
        String ra = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addLiteral(ra, Ns.IM + "structuralPath", "star.ra");
        String unit = edit.createEntity(Ns.IM + "UnitOfMeasurement");
        edit.addLiteral(unit, Ns.RDFS + "label", "deg");
        edit.addRelationship(ra, Ns.IM + "hasUnitOfMeasurement", unit);
        edit.addRelationship(semantics, Ns.IM + "interpretedUsingRecurse", ra);

        String content = edit.createEntity(Ns.IM + "ContentInformation");
        edit.addRelationship(content, Ns.IM + "hasDataObject", dataObject);
        String aip = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
        edit.addLiteral(aip, Ns.RDFS + "label", "Test star catalogue AIP");
        edit.addRelationship(aip, Ns.IM + "hasContentInformation", content);
        String pdi = edit.createEntity(Ns.IM + "PreservationDescriptionInformation");
        edit.addRelationship(aip, Ns.IM + "hasPreservationDescriptiveInformation", pdi);
        String provenance = edit.createEntity(Ns.IM + "ProvenanceInformation");
        edit.addLiteral(provenance, Ns.RDFS + "comment", "Observed in 2026.");
        edit.addRelationship(pdi, Ns.IM + "hasProvenanceInformation", provenance);
        return Map.of("dataObject", dataObject, "dfdl", dfdl, "unit", unit, "aip", aip, "provenance", provenance);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static String text(Map<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    private static Map<String, String> manifest(String text) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int space = line.indexOf("  ");
            entries.put(line.substring(space + 2), line.substring(0, space));
        }
        return entries;
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
}
