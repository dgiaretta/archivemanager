package info.oais.archive.manager;

import com.sun.net.httpserver.HttpServer;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.security.EditAuthInterceptor;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.format.RepInfoGroupMigration;
import info.oais.archive.manager.service.format.StorageFetcher;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.RepInfoManifest;
import info.oais.infomodel.structure.manifest.StructureDescription;
import info.oais.infomodel.structure.manifest.ViewDescription;
import info.oais.infomodel.structure.topcat.OaisStructureTableBuilder;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.util.DataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Viewing data with TOPCAT and SPLAT: RepInfo Tools' bundle (a manifest, the
 * DFDL and DRB SDF descriptions and a table view) opened by the real TOPCAT
 * reader; and a Data Object saved to the archive, with its bits at a storage
 * location, served as a manifest and as VOTable. Private addresses are
 * allowed for fetching here, since the "storage" is a server on this machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "archive.fetch.allow-private-addresses=true")
class ViewersTest {

    private static final String CSV = "AB12,32,-45\nXY9,33,215\n";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RdfStore store;
    @Autowired
    private EditService edit;
    @Autowired
    private ArchiveService archive;
    @Autowired
    private QueryRunner q;
    @Autowired
    private RepInfoGroupMigration migration;

    @TempDir
    Path dir;

    @Test
    void downloadsABundleTopcatOpensThenServesTheSavedDataObject() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "csv").session(session));
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("Open in TOPCAT or SPLAT")));

        byte[] zip = mockMvc.perform(get("/repinfo-tools/download/viewers").param("dataFile", "my readings.csv")
                .session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Path bundle = unzip(zip);
        Files.writeString(bundle.resolve("my_readings.csv"), CSV);
        Path manifest;
        try (var files = Files.list(bundle)) {
            manifest = files.filter(p -> p.toString().endsWith(".ttl")).findFirst().orElseThrow();
        }
        StarTable table = new OaisStructureTableBuilder().makeStarTable(DataSource.makeDataSource(manifest.toString()),
                false, StoragePolicy.PREFER_MEMORY);
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(table.getColumnInfo(2).getName()).isEqualTo("temperature");
        assertThat(table.getColumnInfo(2).getUnitString()).isEqualTo("Cel");
        assertThat(table.getCell(1, 0)).isEqualTo("XY9");
        assertThat(table.getCell(1, 2)).isEqualTo(215);

        String location = mockMvc.perform(post("/repinfo-tools/save").param("formats", "dfdl", "drb-java")
                .session(session)).andReturn().getResponse().getRedirectedUrl();
        String dataObject = archive.decodeId(location.substring("/resource/".length()));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/readings.csv", exchange -> {
            byte[] body = CSV.getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String storage = "http://127.0.0.1:" + server.getAddress().getPort() + "/readings.csv";
            write(() -> {
                edit.addRelationship(dataObject, Ns.BRIDGE + "hasStorageLocation", storage);
                return null;
            });
            String id = archive.encodeId(dataObject);

            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("View in TOPCAT")))
                    .andExpect(content().string(containsString("/api/data-objects/" + id + "/repinfo.ttl")));

            String turtle = mockMvc.perform(get("/api/data-objects/{id}/repinfo.ttl", id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            DescribedData described = RepInfoManifest.read(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                    URI.create("http://localhost/api/data-objects/" + id + "/repinfo.ttl")).select(null);
            assertThat(described.data()).isEqualTo(URI.create(storage));
            assertThat(described.structures()).extracting(StructureDescription::language)
                    .containsExactlyInAnyOrder("DFDL", "DRB SDF");
            assertThat(described.view(ViewDescription.TABLE)).isPresent();
            assertThat(described.meaningOf("reading", "temperature").orElseThrow().units()).isEqualTo("Cel");

            String dfdlUrl = described.structures().stream().filter(s -> s.language().equals("DFDL")).findFirst()
                    .orElseThrow().location().getPath();
            mockMvc.perform(get(dfdlUrl)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("dfdl:format")));

            mockMvc.perform(get("/api/data-objects/{id}/votable", id)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("<VOTABLE")))
                    .andExpect(content().string(containsString("unit=\"Cel\"")))
                    .andExpect(content().string(containsString("XY9")));
        } finally {
            server.stop(0);
            removeReachable(dataObject);
        }
    }

    @Test
    void refusesToFetchFromThisServersOwnNetwork() {
        StorageFetcher strict = new StorageFetcher(1000, false, 5);
        assertThatThrownBy(() -> strict.fetch(URI.create("http://127.0.0.1:9/x"), dir))
                .hasMessageContaining("not a public internet address");
        assertThatThrownBy(() -> strict.fetch(URI.create("file:///etc/passwd"), dir))
                .hasMessageContaining("only fetches data from http and https");
        assertThatThrownBy(() -> strict.fetch(URI.create("http://10.1.2.3/x"), dir))
                .hasMessageContaining("not a public internet address");
    }

    @Test
    void movesOldDescriptionTextsIntoTheirOwnProperty() {
        String ri = write(() -> {
            String structure = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(structure, Ns.RDFS + "comment",
                    "Byte/logical layout of \"Old\" as a DFDL description:\n\n<xs:schema/>");
            return structure;
        });
        try {
            assertThat(write(() -> migration.moveSpecificationTexts())).isEqualTo(1);
            store.beginTransaction(ReadWrite.READ);
            try {
                assertThat(q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?text ?language ?comment WHERE { <%s> im:specificationText ?text ;
                            im:specificationLanguage ?language ; rdfs:comment ?comment }""".formatted(ri)))
                        .containsExactly(java.util.Map.of("text", "<xs:schema/>", "language", "DFDL",
                                "comment", "Layout of \"Old\" as a DFDL description."));
            } finally {
                store.endTransaction(true);
            }
        } finally {
            removeReachable(ri);
        }
    }

    private Path unzip(byte[] zip) throws IOException {
        Path root = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                Path file = dir.resolve(e.getName());
                Files.createDirectories(file.getParent());
                Files.write(file, in.readAllBytes());
                root = file.getParent();
            }
        }
        return root;
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
