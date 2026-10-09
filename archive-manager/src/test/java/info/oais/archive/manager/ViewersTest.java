package info.oais.archive.manager;

import com.sun.net.httpserver.HttpServer;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.security.EditAuthInterceptor;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.RepInfoGroupMigration;
import info.oais.archive.manager.service.format.StorageFetcher;
import info.oais.archive.manager.service.format.ViewerBundle;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import info.oais.infomodel.structure.image.DecodedImage;
import info.oais.infomodel.structure.image.OaisStructureImage;
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
import org.apache.jena.vocabulary.RDF;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Viewing data with TOPCAT and SPLAT: RepInfo Tools' bundle (a manifest, the
 * DFDL and DRB SDF descriptions and a table view) opened by the real TOPCAT
 * reader; and a Data Object saved to the archive, with its bits at a storage
 * location, served as a manifest and as VOTable. Images likewise, with an
 * image view, opened by oais-structure-image and served as FITS for DS9,
 * Aladin and Fiji. Private addresses are allowed for fetching here, since the
 * "storage" is a server on this machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "archive.fetch.allow-private-addresses=true")
class ViewersTest {

    private static final String CSV = "AB12,32,-45\nXY9,33,215\n";

    /** A 4 x 3 image of unsigned 16-bit counts: width, height, then the rows. */
    private static final int[][] PIXELS = {{0, 1, 2, 3}, {100, 200, 300, 400}, {65535, 40000, 7, 8}};

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
    @Autowired
    private info.oais.archive.manager.service.LocalExtensionsMigration localExtensionsMigration;

    @TempDir
    Path dir;

    @Test
    void downloadsABundleTopcatOpensThenServesTheSavedDataObject() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "csv").session(session));
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("Open in TOPCAT, SPLAT or an image viewer")));

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
                edit.addRelationship(dataObject, Ns.IM + "hasStorageLocation", storage);
                return null;
            });
            String id = archive.encodeId(dataObject);

            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("View with TOPCAT")))
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

            // Its right-click menu in the graph offers TOPCAT -- not SPLAT: the station column is text.
            mockMvc.perform(get("/api/graph/{id}", id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + dataObject + "')].viewers[*].id").value(
                            org.hamcrest.Matchers.contains("topcat")))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + dataObject + "')].viewers[0].dataUrl").value(
                            org.hamcrest.Matchers.contains("/api/data-objects/" + id + "/votable")));
            mockMvc.perform(get("/graph/{id}", id))
                    .andExpect(content().string(containsString("/js/samp-send.js")))
                    .andExpect(content().string(containsString("View the data")));
        } finally {
            server.stop(0);
            removeReachable(dataObject);
        }
    }

    /**
     * A Data Object's values printed a page at a time, with their meanings from the saved Semantic
     * Representation Information, decoded with whichever saved description is chosen.
     */
    @Test
    void printsADataObjectsValuesAPageAtATime() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "telemetry").session(session));
        String telemetry = archive.decodeId(mockMvc.perform(post("/repinfo-tools/save")
                        .param("formats", "dfdl", "drb-java", "east").session(session))
                .andReturn().getResponse().getRedirectedUrl().substring("/resource/".length()));
        mockMvc.perform(post("/repinfo-tools/start").param("template", "csv").session(session));
        String readings = archive.decodeId(mockMvc.perform(post("/repinfo-tools/save").param("formats", "dfdl")
                .session(session)).andReturn().getResponse().getRedirectedUrl().substring("/resource/".length()));
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            csv.append("S").append(i).append(',').append(i % 366).append(',').append(i - 150).append('\n');
        }
        byte[] tlm = GeneratedDescriptionsMatrixTest.telemetryBytes();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().endsWith(".csv")
                    ? csv.toString().getBytes(StandardCharsets.US_ASCII) : tlm;
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            write(() -> {
                edit.addRelationship(telemetry, Ns.IM + "hasStorageLocation", base + "/packets.tlm");
                edit.addRelationship(readings, Ns.IM + "hasStorageLocation", base + "/readings.csv");
                return null;
            });
            String id = archive.encodeId(telemetry);
            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("See the values here")))
                    .andExpect(content().string(containsString("/api/data-objects/" + id + "/values.json")))
                    .andExpect(content().string(containsString("<option value=\"EAST\">EAST</option>")))
                    .andExpect(content().string(containsString("/js/values-viewer.js")));

            // The first page, decoded with its DFDL description (the preferred one): meanings as RepInfo Tools shows them.
            String first = mockMvc.perform(get("/api/data-objects/{id}/values.json", id).param("size", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.language").value("DFDL"))
                    .andExpect(jsonPath("$.languages").value(org.hamcrest.Matchers.contains("DFDL", "DRB SDF", "EAST")))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(first).contains("= 28 V").contains("= 300 K").contains("housekeeping");
            long total = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("totalRows").asLong();

            // Small pages cover the same rows, in order.
            List<String> paged = new java.util.ArrayList<>();
            for (int page = 1; page <= (total + 4) / 5; page++) {
                String json = mockMvc.perform(get("/api/data-objects/{id}/values.json", id)
                        .param("page", String.valueOf(page)).param("size", "5"))
                        .andExpect(jsonPath("$.page").value(page))
                        .andExpect(jsonPath("$.totalPages").value((total + 4) / 5))
                        .andReturn().getResponse().getContentAsString();
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("rows")
                        .forEach(r -> paged.add(r.get("name").asText() + "=" + r.get("value").asText()));
            }
            List<String> whole = new java.util.ArrayList<>();
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("rows")
                    .forEach(r -> whole.add(r.get("name").asText() + "=" + r.get("value").asText()));
            assertThat(paged).containsExactlyElementsOf(whole);

            // The same data, decoded with its EAST description, by the EAST interpreter.
            mockMvc.perform(get("/api/data-objects/{id}/values.json", id).param("language", "EAST").param("size", "1000"))
                    .andExpect(jsonPath("$.language").value("EAST"))
                    .andExpect(jsonPath("$.error").doesNotExist())
                    .andExpect(jsonPath("$.rows[?(@.meaning =~ /.*= 28 V.*/)]").isNotEmpty());
            mockMvc.perform(get("/api/data-objects/{id}/values.json", id).param("language", "Kaitai Struct"))
                    .andExpect(jsonPath("$.error").value(containsString("no Kaitai Struct description")));

            // Many rows: a page past the file's index blocks, and a page beyond the last is the last.
            String readingsId = archive.encodeId(readings);
            mockMvc.perform(get("/api/data-objects/{id}/values.json", readingsId).param("page", "9").param("size", "50"))
                    .andExpect(jsonPath("$.totalRows").value(1 + 300 * 4))
                    .andExpect(jsonPath("$.rows[0].name").value("temperature"))
                    .andExpect(jsonPath("$.rows[0].value").value("-51"))
                    .andExpect(jsonPath("$.rows[1].name").value("reading[100]"));
            mockMvc.perform(get("/api/data-objects/{id}/values.json", readingsId).param("page", "1000").param("size", "50"))
                    .andExpect(jsonPath("$.page").value(25))
                    .andExpect(jsonPath("$.rows.length()").value(1));
        } finally {
            server.stop(0);
            removeReachable(telemetry);
            removeReachable(readings);
        }
    }

    @Test
    void offersSplatWhenTheTableViewIsAllNumbersAndNothingWithoutAStorageLocation() throws Exception {
        String[] objects = write(() -> {
            String spectrum = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addRelationship(spectrum, Ns.IM + "hasStorageLocation", "https://example.org/spectrum.csv");
            String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
            edit.addRelationship(spectrum, Ns.IM + "interpretedUsing", top);
            String dfdl = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(dfdl, Ns.IM + "specificationLanguage", "DFDL");
            edit.addLiteral(dfdl, Ns.IM + "specificationText", "<xs:schema/>");
            String semantics = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
            String view = edit.createEntity(Ns.IM + "ViewSpecification");
            edit.addLiteral(view, Ns.IM + "viewKind", "table");
            edit.addLiteral(view, Ns.IM + "specificationText", "<tableView><rows select=\"children\" name=\"row\"/>"
                    + "<columns><column name=\"wavelength\" type=\"double\"/><column name=\"flux\" type=\"double\"/>"
                    + "</columns></tableView>");
            edit.addRelationship(top, Ns.IM + "hasGroupMember", dfdl);
            edit.addRelationship(top, Ns.IM + "hasGroupMember", semantics);
            edit.addRelationship(semantics, Ns.IM + "interpretedUsingRecurse", view);
            String unstored = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addRelationship(unstored, Ns.IM + "interpretedUsing", top);
            return new String[] {spectrum, unstored};
        });
        try {
            mockMvc.perform(get("/api/graph/{id}", archive.encodeId(objects[0])))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + objects[0] + "')].viewers[*].id").value(
                            org.hamcrest.Matchers.containsInAnyOrder("topcat", "splat")))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + objects[0] + "')].viewers[?(@.id == 'splat')].mtype")
                            .value(org.hamcrest.Matchers.contains("spectrum.load.ssa-generic")));
            mockMvc.perform(get("/api/graph/{id}", archive.encodeId(objects[1])))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + objects[1] + "')].viewers[*]").isEmpty());
            // A resource with nothing to view still has a page, without the viewer script.
            mockMvc.perform(get("/resource/{id}", archive.encodeId(objects[1]))).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("samp-send.js"))));
            mockMvc.perform(get("/resource/{id}", archive.encodeId(objects[0])))
                    .andExpect(content().string(containsString("View with SPLAT")))
                    .andExpect(content().string(containsString("data-samp-client=\"splat\"")));
        } finally {
            removeReachable(objects[0]);
            removeReachable(objects[1]);
        }
    }

    @Test
    void opensAnImageFromRepInfoToolsDescriptionsWithEitherEngine() throws Exception {
        FormatDescription format = image();
        assertThat(ViewerBundle.tableView(format)).isNull();
        java.util.Map<String, String> files = ViewerBundle.files(format, "tiny", "tiny.bin",
                new DfdlGenerator().generate(format), new DrbGenerator().generateSdfSchema(format));
        assertThat(files).containsKeys("tiny.ttl", "tiny.dfdl.xsd", "tiny.drb.xsd", "tiny-image-view.xml")
                .doesNotContainKey("tiny-table-view.xml");
        assertThat(files.get("tiny-image-view.xml")).contains("<rows select=\"children\" name=\"row\"/>")
                .contains("<pixels select=\"children\" name=\"pixel\"/>").contains("unit=\"ADU\"");
        assertThat(files.get("README.txt")).contains("ManifestToFits");
        for (var file : files.entrySet()) {
            Files.writeString(dir.resolve(file.getKey()), file.getValue());
        }
        Files.write(dir.resolve("tiny.bin"), imageBytes());
        DescribedData described = RepInfoManifest.read(dir.resolve("tiny.ttl").toUri()).select(null);
        assertThat(described.view(ViewDescription.IMAGE)).isPresent();
        for (String language : List.of(StructureDescription.DFDL, StructureDescription.DRB_SDF)) {
            DescribedData only = new DescribedData(described.iri(), described.name(), described.data(),
                    described.structures().stream().filter(s -> s.language().equals(language)).toList(),
                    described.views(), described.meanings());
            DecodedImage image = OaisStructureImage.open(only);
            assertThat(image.width()).as(language).isEqualTo(4);
            assertThat(image.height()).as(language).isEqualTo(3);
            for (int r = 0; r < PIXELS.length; r++) {
                for (int c = 0; c < PIXELS[r].length; c++) {
                    assertThat(image.pixels()[r][c].longValue()).as(language + " pixel " + r + "," + c)
                            .isEqualTo(PIXELS[r][c]);
                }
            }
            assertThat(image.unit()).isEqualTo("ADU");
        }
    }

    @Test
    void servesAnImageDataObjectAsFitsForImageViewers() throws Exception {
        FormatDescription format = image();
        String dfdl = new DfdlGenerator().generate(format);
        String view = ViewerBundle.imageView(format);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/tiny.bin", exchange -> {
            byte[] body = imageBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String storage = "http://127.0.0.1:" + server.getAddress().getPort() + "/tiny.bin";
        String dataObject = write(() -> {
            String image = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addLiteral(image, Ns.RDFS + "label", "Tiny image");
            edit.addRelationship(image, Ns.IM + "hasStorageLocation", storage);
            String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
            edit.addRelationship(image, Ns.IM + "interpretedUsing", top);
            String structure = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(structure, Ns.IM + "specificationLanguage", "DFDL");
            edit.addLiteral(structure, Ns.IM + "specificationText", dfdl);
            String semantics = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
            String imageView = edit.createEntity(Ns.IM + "ViewSpecification");
            edit.addLiteral(imageView, Ns.IM + "viewKind", "image");
            edit.addLiteral(imageView, Ns.IM + "specificationText", view);
            edit.addRelationship(top, Ns.IM + "hasGroupMember", structure);
            edit.addRelationship(top, Ns.IM + "hasGroupMember", semantics);
            edit.addRelationship(semantics, Ns.IM + "interpretedUsingRecurse", imageView);
            return image;
        });
        try {
            String id = archive.encodeId(dataObject);
            mockMvc.perform(get("/api/graph/{id}", id))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + dataObject + "')].viewers[*].id").value(
                            org.hamcrest.Matchers.contains("ds9", "aladin")))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + dataObject + "')].viewers[0].dataUrl").value(
                            org.hamcrest.Matchers.contains("/api/data-objects/" + id + "/fits")))
                    .andExpect(jsonPath("$.nodes[?(@.id == '" + dataObject + "')].viewers[0].mtype").value(
                            org.hamcrest.Matchers.contains("image.load.fits")));
            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("View with DS9")))
                    .andExpect(content().string(containsString("data-samp-client=\"aladin\"")))
                    .andExpect(content().string(containsString("/api/data-objects/" + id + "/fits")))
                    .andExpect(content().string(containsString("Fiji/ImageJ")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("View with TOPCAT"))));

            byte[] fits = mockMvc.perform(get("/api/data-objects/{id}/fits", id)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/fits"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(fits.length % 2880).isZero();
            String header = new String(fits, 0, 2880, StandardCharsets.US_ASCII);
            assertThat(header).startsWith("SIMPLE  =                    T")
                    .contains("NAXIS1  =                    4").contains("NAXIS2  =                    3")
                    .contains("BZERO   =                32768").contains("BUNIT   = 'ADU     '")
                    .contains("OBJECT  = 'Tiny image'").contains("HISTORY Decoded from");
            ByteBuffer data = ByteBuffer.wrap(fits, 2880, 4 * 3 * 2);
            for (int[] row : PIXELS) {
                for (int expected : row) {
                    assertThat(data.getShort() + 32768).isEqualTo(expected);
                }
            }

            // The archive's own viewer shows it in the page, from its pixels.
            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("See the image here")))
                    .andExpect(content().string(containsString("/js/image-viewer.js")))
                    .andExpect(content().string(containsString("data-pixels=\"/api/data-objects/" + id
                            + "/pixels.json\"")));
            mockMvc.perform(get("/api/data-objects/{id}/pixels.json", id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.width").value(4)).andExpect(jsonPath("$.height").value(3))
                    .andExpect(jsonPath("$.unit").value("ADU"))
                    .andExpect(jsonPath("$.pixels[0][1]").value(1.0))
                    .andExpect(jsonPath("$.pixels[2][0]").value(65535.0));
        } finally {
            server.stop(0);
            removeReachable(dataObject);
        }
    }

    /** An image as RepInfo Tools describes one: its size, then rows, each a repeated pixel field. */
    private static FormatDescription image() {
        FieldDescription pixel = new FieldDescription("pixel", "pixel", PrimitiveType.UINT16, null, null,
                new Occurrence.Repeated(Expression.parse("width")),
                Semantics.of("Counts", "Detector counts in one pixel", "ADU"));
        RecordDescription row = RecordDescription.of("row", List.of(pixel))
                .withOccurrence(new Occurrence.Repeated(Expression.parse("height")));
        return new FormatDescription("Tiny image", "", info.oais.infomodel.structure.description.ByteOrder.LITTLE_ENDIAN,
                List.of("bin"), RecordDescription.of("tiny_image", List.of(
                        new FieldDescription("width", "width", PrimitiveType.UINT32, null, null, Occurrence.ONCE,
                                Semantics.NONE),
                        new FieldDescription("height", "height", PrimitiveType.UINT32, null, null, Occurrence.ONCE,
                                Semantics.NONE),
                        row)));
    }

    private static byte[] imageBytes() {
        ByteBuffer b = ByteBuffer.allocate(8 + 4 * 3 * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(4).putInt(3);
        for (int[] row : PIXELS) {
            for (int p : row) {
                b.putShort((short) p);
            }
        }
        return b.array();
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
    void renamesTheOldBridgeAndRicTermsOnOaisIndividuals() {
        String[] made = write(() -> {
            String o = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addRelationship(o, Ns.BRIDGE + "hasStorageLocation", "https://example.org/old.bin");
            String ri = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
            edit.addRelationship(o, Ns.IM + "interpretedUsing", ri);
            edit.addLiteral(ri, Ns.BRIDGE + "structuralPath", "reading.temp");
            edit.addLiteral(ri, Ns.BRIDGE + "scaleFactor", "0.1");
            String kelvin = edit.createEntity(Ns.RICO + "UnitOfMeasurement");
            edit.addRelationship(ri, Ns.RICO + "hasUnitOfMeasurement", kelvin);
            String extent = edit.createEntity(Ns.RICO + "Extent");
            String metres = edit.createEntity(Ns.RICO + "UnitOfMeasurement");
            edit.addRelationship(extent, Ns.RICO + "hasUnitOfMeasurement", metres);
            String orphan = edit.createEntity(Ns.RICO + "UnitOfMeasurement");
            edit.addLiteral(orphan, Ns.RDFS + "label", "K");
            return new String[] {o, ri, kelvin, extent, metres, orphan};
        });
        try {
            assertThat(write(() -> localExtensionsMigration.migrate())).isEqualTo(6);
            store.beginTransaction(ReadWrite.READ);
            try {
                Model m = store.dataModel();
                Resource o = m.getResource(made[0]);
                assertThat(o.getPropertyResourceValue(m.createProperty(Ns.IM + "hasStorageLocation")).getURI())
                        .isEqualTo("https://example.org/old.bin");
                assertThat(o.hasProperty(m.createProperty(Ns.BRIDGE + "hasStorageLocation"))).isFalse();
                Resource ri = m.getResource(made[1]);
                assertThat(ri.getProperty(m.createProperty(Ns.IM + "structuralPath")).getString()).isEqualTo("reading.temp");
                assertThat(ri.getProperty(m.createProperty(Ns.IM + "scaleFactor")).getString()).isEqualTo("0.1");
                assertThat(ri.getPropertyResourceValue(m.createProperty(Ns.IM + "hasUnitOfMeasurement")).getURI())
                        .isEqualTo(made[2]);
                assertThat(ri.listProperties().toList()).noneMatch(s -> s.getPredicate().getURI().startsWith(Ns.BRIDGE)
                        || s.getPredicate().getURI().startsWith(Ns.RICO));
                assertThat(m.getResource(made[2]).hasProperty(RDF.type, m.createResource(Ns.IM + "UnitOfMeasurement")))
                        .isTrue();
                assertThat(m.getResource(made[2]).hasProperty(RDF.type, m.createResource(Ns.RICO + "UnitOfMeasurement")))
                        .isFalse();
                // A unit nothing uses any more was RepInfo Tools', so it is OAIS's too.
                assertThat(m.getResource(made[5]).hasProperty(RDF.type, m.createResource(Ns.IM + "UnitOfMeasurement")))
                        .isTrue();
                assertThat(m.getResource(made[5]).hasProperty(RDF.type, m.createResource(Ns.RICO + "UnitOfMeasurement")))
                        .isFalse();
                // An Extent's unit is RiC-O's, not OAIS's, and is left alone.
                assertThat(m.getResource(made[3]).getPropertyResourceValue(m.createProperty(Ns.RICO + "hasUnitOfMeasurement"))
                        .getURI()).isEqualTo(made[4]);
                assertThat(m.getResource(made[4]).hasProperty(RDF.type, m.createResource(Ns.RICO + "UnitOfMeasurement")))
                        .isTrue();
            } finally {
                store.endTransaction(true);
            }
            assertThat(write(() -> localExtensionsMigration.migrate())).isZero();
        } finally {
            removeReachable(made[0]);
            removeReachable(made[3]);
            removeReachable(made[5]);
        }
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
