package info.oais.archive.manager;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.EditService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An AIP stored as a package: the archive opens it and maps it to the AIP components. */
@SpringBootTest
@AutoConfigureMockMvc
class PackageMappingTest {

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

    @TempDir
    Path dir;

    @Test
    void mapsThePackageAnAipIsStoredAs() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        Path file = PackageInspectorTest.sevenZip(dir.resolve("aip.7z"), PackageInspectorTest.eternalPackage());
        BitStore.StoredBits stored = bits.store(Files.readAllBytes(file), "GHG-aip.7z");
        try {
            // The separate AIP holding the Semantic Representation Information, as its Semantics identifies it.
            String semanticsAip = write(() -> {
                String a = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
                edit.addLiteral(a, Ns.RDFS + "label", "Semantics of the GHG inventory");
                edit.addRelationship(a, Ns.IM + "hasStorageLocation",
                        "https://example.org/NAM/SemRI-6ffddacf-89ed-450c-b142-7427fb109cce.7z");
                return a;
            });
            String aip = write(() -> {
                String a = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
                edit.addLiteral(a, Ns.RDFS + "label", "AIP for CC-00003");
                edit.addRelationship(a, Ns.IM + "hasStorageLocation", "http://localhost" + stored.path());
                return a;
            });
            String id = archive.encodeId(aip);

            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("/packages/" + id + "/contents")))
                    .andExpect(content().string(containsString("this AIP's package may hold the rest")));

            mockMvc.perform(get("/packages/{id}/contents", id)).andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Every component OAIS requires of an AIP is in the package.")))
                    .andExpect(content().string(containsString("The bag is complete and unchanged:")))
                    .andExpect(content().string(containsString("PRONOM x-fmt/238")))
                    .andExpect(content().string(containsString("metadata.csv: Rights")))
                    .andExpect(content().string(containsString("href=\"/resource/" + archive.encodeId(semanticsAip)
                            + "\">Semantics of the GHG inventory")));

            byte[] csv = mockMvc.perform(get("/packages/{id}/contents.csv", id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            String text = new String(csv, StandardCharsets.UTF_8);
            assertThat(text).startsWith("﻿AIP component,Found,Where to find it in the package,What it says")
                    .contains("\"PDI: Access Rights Information\",yes,\"metadata.csv: Rights\"")
                    .contains("\"PDI: Context Information\",yes,\"metadata.csv: Relation\"");

            String unpackaged = write(() -> edit.createEntity(Ns.IM + "ArchivalInformationPackage"));
            mockMvc.perform(get("/packages/{id}/contents", archive.encodeId(unpackaged)))
                    .andExpect(content().string(containsString("no storage location that is a package")));
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
