package info.oais.archive.manager;

import info.oais.archive.manager.security.EditAuthInterceptor;
import info.oais.archive.manager.service.format.HandWrittenDescriptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepInfoToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void decodesAnUploadedSampleWithTheDraftsDfdl() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (int i = 0; i < 10; i++) {
            header.writeBytes(String.format("%-80s", i == 9 ? "END" : "KEY" + i + "    = 1")
                    .getBytes(StandardCharsets.US_ASCII));
        }

        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "header.fits", "application/octet-stream",
                                header.toByteArray()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("KEY1    = 1")));
    }

    @Test
    void showsDiagnosticsForASampleThatDoesNotMatch() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();

        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "short.bin", "application/octet-stream", new byte[5]))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("could not be decoded")));
    }

    @Test
    void decodesAnUploadedSampleWithTheDraftsDrbSdfSchema() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (int i = 0; i < 10; i++) {
            header.writeBytes(String.format("%-80s", i == 9 ? "END" : "KEY" + i + "    = 1")
                    .getBytes(StandardCharsets.US_ASCII));
        }

        mockMvc.perform(multipart("/repinfo-tools/test-drb-java")
                        .file(new MockMultipartFile("sample", "header.fits", "application/octet-stream",
                                header.toByteArray()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("KEY1    = 1")))
                .andExpect(content().string(containsString("bytes 80–159")));
    }

    @Test
    void downloadsTheDrbPythonDriverAsAnInstallablePackage() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();

        byte[] zip = mockMvc.perform(get("/repinfo-tools/download/drb-python").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.add(e.getName());
            }
        }
        assertThat(entries).contains("drb-driver-am-fits-primary-header/pyproject.toml",
                "drb-driver-am-fits-primary-header/drb/topics/am_fits_primary_header/cortex.ttl");
    }

    @Test
    void buildsARecordWithAChoiceARepeatAndSemanticsInTheTreeEditor() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String temp = buildTelemetry(session);

        mockMvc.perform(get("/repinfo-tools/edit").param("element", temp).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("repeated n times")))
                .andExpect(content().string(containsString("one of the branches below, chosen by kind")))
                .andExpect(content().string(containsString("when kind = 2")))
                .andExpect(content().string(containsString("Temperature [K]")))
                .andExpect(content().string(containsString("2 coded values")))
                .andExpect(content().string(containsString("https://qudt.org/vocab/unit/K")));

        mockMvc.perform(get("/repinfo-tools/download/kaitai").session(session))
                .andExpect(content().string(containsString("repeat-expr: 'n'")))
                .andExpect(content().string(containsString("switch-on: 'kind'")))
                .andExpect(content().string(containsString("Codes: 1 = housekeeping; 2 = science.")))
                .andExpect(content().string(containsString("Temperature (K). Physical value = raw * 0.01 + 200.")));
    }

    @Test
    void explainsABadExpressionInsteadOfSavingIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "blank").session(session));
        String items = addElement(session, "root", "field", "items", null, null);

        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", items).session(session)
                        .param("name", "items").param("type", "UINT8").param("occurs", "repeated").param("occursExpr", "n *"))
                .andExpect(flash().attribute("editorError", containsString("Expression ends too early")));
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", items).session(session)
                        .param("name", "items").param("type", "UINT8").param("occurs", "repeated").param("occursExpr", "n"))
                .andExpect(flash().attribute("editorMessage", "Saved 'items'."));
        mockMvc.perform(get("/repinfo-tools/edit").session(session))
                .andExpect(content().string(containsString("uses &#39;n&#39;, which isn&#39;t a field read earlier")));
    }

    /**
     * Builds, through the editor's endpoints, a "Telemetry" format: a count
     * {@code n}, then {@code n} packets of a coded {@code kind}, a choice on it
     * (branch 2: a scaled temperature in K), and an extra byte only when
     * {@code kind = 3}. Returns the temperature field's id.
     */
    private String buildTelemetry(MockHttpSession session) throws Exception {
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "blank").session(session));
        mockMvc.perform(post("/repinfo-tools/details").param("name", "Telemetry").param("defaultByteOrder", "BIG_ENDIAN")
                .session(session));

        String n = addElement(session, "root", "field", "n", null, null);
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", n).session(session)
                .param("name", "n").param("type", "UINT16").param("occurs", "once")
                .param("semanticName", "Packet count").param("definition", "How many packets follow"));
        String packet = addElement(session, "root", "record", "packet", null, null);
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", packet).session(session)
                .param("name", "packet").param("occurs", "repeated").param("occursExpr", "n"));
        String kind = addElement(session, packet, "field", "kind", null, null);
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", kind).session(session)
                .param("name", "kind").param("type", "UINT8").param("occurs", "once")
                .param("codes", "1 = housekeeping\n2 = science"));
        String body = addElement(session, packet, "choice", "body", "kind", null);
        String science = addElement(session, body, "branch", "science", null, "2");
        String temp = addElement(session, science, "field", "temp", null, null);
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", temp).session(session)
                .param("name", "temp").param("type", "UINT16").param("occurs", "once")
                .param("semanticName", "Temperature").param("units", "K").param("scale", "0.01").param("offset", "200")
                .param("unitsUri", "https://qudt.org/vocab/unit/K"));
        String extra = addElement(session, packet, "field", "extra", null, null);
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", extra).session(session)
                .param("name", "extra").param("type", "UINT8").param("occurs", "optional").param("occursExpr", "kind = 3"));
        return temp;
    }

    @Test
    void writesASampleBackUnchangedAndWithChangesThenOffersTheFile() throws Exception {
        MockHttpSession session = new MockHttpSession();
        buildTelemetry(session);
        byte[] sample = {0, 1, 2, 0x01, (byte) 0xF4};

        for (String engine : new String[] {"dfdl", "drb-java", "kaitai"}) {
            mockMvc.perform(multipart("/repinfo-tools/write-back/" + engine)
                            .file(new MockMultipartFile("sample", "t.bin", "application/octet-stream", sample))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Identical: all 5 bytes were written back exactly.")));
        }

        mockMvc.perform(multipart("/repinfo-tools/write-back/dfdl")
                        .file(new MockMultipartFile("sample", "t.bin", "application/octet-stream", sample))
                        .param("changes", "# a comment\n/packet/body/science/temp = 1000\n")
                        .session(session))
                .andExpect(content().string(containsString("written back\n                <span>with the changes</span>")))
                .andExpect(content().string(containsString("2 bytes differ, the first at offset 3.")));
        byte[] written = mockMvc.perform(get("/repinfo-tools/write-back/download").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        "Content-Disposition", "attachment; filename=\"t-changed-dfdl.bin\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(written).containsExactly(0, 1, 2, 0x03, (byte) 0xE8);

        mockMvc.perform(multipart("/repinfo-tools/write-back/dfdl")
                        .file(new MockMultipartFile("sample", "t.bin", "application/octet-stream", sample))
                        .param("changes", "/packet/kind 3")
                        .session(session))
                .andExpect(content().string(containsString("Line 1 isn&#39;t a change: write path = value")));
        mockMvc.perform(multipart("/repinfo-tools/write-back/drb-java")
                        .file(new MockMultipartFile("sample", "t.bin", "application/octet-stream", sample))
                        .param("changes", "/packet/nothing = 3")
                        .session(session))
                .andExpect(content().string(containsString("There&#39;s no element /packet/nothing")));
    }

    @Test
    void parsesChangesLineByLine() {
        var changes = info.oais.archive.manager.web.RepInfoToolController.parseChanges("/a = 1\n\n# skipped\n/b[2]/c = \"  spaced  \"\r\n/d =\n");
        assertThat(changes).containsExactly(
                java.util.Map.entry(info.oais.infomodel.structure.ElementPath.parse("/a"), "1"),
                java.util.Map.entry(info.oais.infomodel.structure.ElementPath.parse("/b[2]/c"), "  spaced  "),
                java.util.Map.entry(info.oais.infomodel.structure.ElementPath.parse("/d"), ""));
    }

    @Test
    void sampleResultsShowMeaningsBranchesAbsenceAndLeftoverBytes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        buildTelemetry(session);
        // n = 1; one packet: kind 2 (science), temp 500 (= 205 K); then one byte the description doesn't cover.
        byte[] sample = {0, 1, 2, 0x01, (byte) 0xF4, (byte) 0xFF};

        for (String engine : new String[] {"test-dfdl", "test-drb-java"}) {
            mockMvc.perform(multipart("/repinfo-tools/" + engine)
                            .file(new MockMultipartFile("sample", "t.bin", "application/octet-stream", sample))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("decoded successfully")))
                    .andExpect(content().string(containsString("science")))
                    .andExpect(content().string(containsString("chose science")))
                    .andExpect(content().string(containsString("= 205 K")))
                    .andExpect(content().string(containsString("absent: its condition is false")))
                    .andExpect(content().string(containsString(
                            "1 byte at the end of the file are not covered by the description")));
        }
    }

    @Test
    void startsFromTheTelemetryAndCsvTemplates() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "telemetry").session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/repinfo-tools/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Telemetry packets (example)")))
                .andExpect(content().string(containsString("one of the branches below, chosen by packet_type")));
        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "t.tlm", "application/octet-stream",
                                GeneratedDescriptionsMatrixTest.telemetryBytes()))
                        .session(session))
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("= 28 V")))
                .andExpect(content().string(containsString("= 300 K")));

        mockMvc.perform(post("/repinfo-tools/start").param("template", "csv").session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/repinfo-tools/download/dfdl").session(session))
                .andExpect(content().string(containsString("dfdl:separator=\",\"")));
    }

    @Test
    void choosingDfdlOnlyOffersItsFeaturesAndGeneratesOnlyDfdl() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "blank").session(session));
        String row = addElement(session, "root", "record", "row", null, null);
        String note = addElement(session, row, "field", "note", null, null);

        // With all four languages, the DFDL-only options aren't offered.
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", row).session(session)
                .param("name", "row").param("occurs", "until_end").param("text", "on")
                .param("fieldSeparator", ",").param("recordTerminator", "\\n"));
        mockMvc.perform(get("/repinfo-tools/edit").param("element", note).session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"nilValue\""))));

        mockMvc.perform(post("/repinfo-tools/details").session(session).param("name", "Notes")
                .param("defaultByteOrder", "BIG_ENDIAN").param("targets", "DFDL"));
        mockMvc.perform(get("/repinfo-tools/edit").param("element", note).session(session))
                .andExpect(content().string(containsString("name=\"nilValue\"")))
                .andExpect(content().string(containsString("name=\"numberPattern\"")));
        mockMvc.perform(get("/repinfo-tools/edit").param("element", row).session(session))
                .andExpect(content().string(containsString("name=\"quote\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"zlib\""))));

        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", row).session(session)
                .param("name", "row").param("occurs", "until_end").param("text", "on").param("quote", "\"")
                .param("fieldSeparator", ",").param("recordTerminator", "\\n"));
        mockMvc.perform(post("/repinfo-tools/elements/{id}/update", note).session(session)
                .param("name", "note").param("type", "STRING").param("occurs", "once")
                .param("nilShown", "1").param("nil", "on").param("nilValue", "NA"));

        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("Not generated: Kaitai Struct isn&#39;t one of this description&#39;s languages")))
                .andExpect(content().string(containsString("dfdl:escapeSchemeRef")));
        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "n.csv", "text/csv",
                                "\"a, b\"\nNA\n".getBytes(StandardCharsets.US_ASCII)))
                        .session(session))
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("a, b")))
                .andExpect(content().string(containsString("no value (nil)")));

        // Adding a language that can't express them flags each element using them.
        mockMvc.perform(post("/repinfo-tools/details").session(session).param("name", "Notes")
                .param("defaultByteOrder", "BIG_ENDIAN").param("targets", "DFDL", "KAITAI"));
        mockMvc.perform(get("/repinfo-tools/edit").session(session))
                .andExpect(content().string(containsString("uses nil values in delimited text, which Kaitai Struct can&#39;t express")));
    }

    @Test
    void testsTheKaitaiDescriptionAgainstASampleFile() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "telemetry").session(session));

        mockMvc.perform(multipart("/repinfo-tools/test-kaitai")
                        .file(new MockMultipartFile("sample", "t.tlm", "application/octet-stream",
                                GeneratedDescriptionsMatrixTest.telemetryBytes()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("chose science")))
                .andExpect(content().string(containsString("= 300 K")))
                .andExpect(content().string(containsString("The description accounts for every byte of the sample.")));
    }

    @Test
    void writesADfdlDescriptionByHandTestsItAndRefusesUnsafeOnes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "hand-dfdl").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/repinfo-tools/hand/dfdl"));
        mockMvc.perform(get("/repinfo-tools/hand/dfdl").session(session))
                .andExpect(content().string(containsString("Header checks and a header checksum")))
                .andExpect(content().string(containsString("gzip-compressed section")));
        String gzip = HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals("dfdl-gzip"))
                .findFirst().orElseThrow().text();
        mockMvc.perform(get("/repinfo-tools/hand/dfdl").param("example", "dfdl-gzip").session(session))
                .andExpect(content().string(containsString("fixedLengthLayer.dfdl.xsd")));

        mockMvc.perform(post("/repinfo-tools/hand/dfdl").param("text",
                        gzip.replace("/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd", "http://example.org/x.xsd"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("xs:include of &#39;http://example.org/x.xsd&#39; isn&#39;t allowed")));
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Written by hand"))));

        String checksum = HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals("dfdl-checksum"))
                .findFirst().orElseThrow().text();
        mockMvc.perform(post("/repinfo-tools/hand/dfdl").param("text", checksum).session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("Written by hand")))
                .andExpect(content().string(containsString("The header checksum doesn&#39;t match.")));
        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "h.bin", "application/octet-stream",
                                new byte[] {'H', 'D', 'R', 1, 2, 3, 7}))
                        .session(session))
                .andExpect(content().string(containsString("could not be decoded")))
                .andExpect(content().string(containsString("The header checksum doesn&#39;t match.")));
        mockMvc.perform(get("/repinfo-tools/download/dfdl").session(session))
                .andExpect(content().string(containsString("urn:example:checked-header")));
        mockMvc.perform(get("/repinfo-tools/edit").session(session))
                .andExpect(content().string(containsString("Written by hand:")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("has no elements yet"))));

        mockMvc.perform(post("/repinfo-tools/hand/dfdl/revert").session(session));
        mockMvc.perform(get("/repinfo-tools/download/dfdl").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("urn:example:checked-header"))));
    }

    @Test
    void extendsAGeneratedKaitaiDescriptionByHandAndTestsIt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        buildTelemetry(session);
        // Starts from what the tree generates.
        mockMvc.perform(get("/repinfo-tools/hand/kaitai").session(session))
                .andExpect(content().string(containsString("switch-on: &#39;kind&#39;")));

        String stanzas = HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals("kaitai-stanzas"))
                .findFirst().orElseThrow().text();
        mockMvc.perform(post("/repinfo-tools/hand/kaitai").param("text", stanzas).session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/repinfo-tools/test-kaitai")
                        .file(new MockMultipartFile("sample", "s.txt", "text/plain",
                                "name: A\nage: 3\n\nname: B\n".getBytes(StandardCharsets.US_ASCII)))
                        .session(session))
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("name: B")));

        // Changing the tree afterwards is pointed out.
        String extra = addElement(session, "root", "field", "trailer", null, null);
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("The element tree has changed since it was written.")));
        assertThat(extra).isNotBlank();
    }

    @Test
    void addsAHandWrittenAddInToTheDrbPythonDriverPackage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        buildTelemetry(session);
        // A new add-in starts from the documented hooks, not from the generated driver.
        mockMvc.perform(get("/repinfo-tools/hand/drb-python").session(session))
                .andExpect(content().string(containsString("def prepare(data):")))
                .andExpect(content().string(containsString("doesn't run add-ins in sample tests")));

        String crc = HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals("drb-python-crc32"))
                .findFirst().orElseThrow().text();
        mockMvc.perform(post("/repinfo-tools/hand/drb-python").param("text", crc).session(session))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(containsString("addin.py, written by hand")))
                .andExpect(content().string(containsString("_metadata")));

        byte[] zip = mockMvc.perform(get("/repinfo-tools/download/drb-python").session(session))
                .andReturn().getResponse().getContentAsByteArray();
        java.util.Map<String, String> files = new java.util.HashMap<>();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName().substring(entry.getName().indexOf('/') + 1),
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(files).containsKey("pyproject.toml");
        assertThat(files.get("pyproject.toml")).contains("[project.entry-points.\"drb.addon\"]");
        assertThat(files.entrySet()).anyMatch(e -> e.getKey().endsWith("/addin.py") && e.getValue().equals(crc));

        // The edit page keeps checking the tree: an add-in doesn't replace it.
        mockMvc.perform(post("/repinfo-tools/hand/drb-python/revert").session(session));
        mockMvc.perform(get("/repinfo-tools/preview").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("addin.py, written by hand"))));
    }

    /** Adds an element through the editor and returns its id (taken from the redirect). */
    private String addElement(MockHttpSession session, String parentId, String kind, String name, String discriminator,
                              String key) throws Exception {
        var request = post("/repinfo-tools/elements/add").session(session)
                .param("parentId", parentId).param("kind", kind).param("name", name);
        if (discriminator != null) {
            request.param("discriminator", discriminator);
        }
        if (key != null) {
            request.param("key", key);
        }
        String location = mockMvc.perform(request).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return location.substring(location.indexOf("element=") + "element=".length());
    }

    private MockHttpSession loggedInSessionWithFitsDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "fits").session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }
}
