package info.oais.archive.manager;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.security.EditAuthInterceptor;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Describing the columns of data whose format is already known: taking them
 * from a sample, giving them meanings, testing against a sample, and saving
 * the meanings as Semantic Representation Information in an AND group with a
 * format profile and the software that reads the format.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnownFormatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RdfStore store;
    @Autowired
    private QueryRunner q;
    @Autowired
    private info.oais.archive.manager.service.ArchiveService archive;

    private static byte[] workbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Readings");
            Object[][] rows = {{"Station", "Temp", "Status"}, {"AB12", 205, 1}, {"XY9", 999, 3}, {"CD4", -9999, 2}};
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] instanceof Integer n) {
                        row.createCell(c).setCellValue(n);
                    } else {
                        row.createCell(c).setCellValue((String) rows[r][c]);
                    }
                }
            }
            wb.createSheet("Empty");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockHttpSession loggedIn() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        return session;
    }

    /** The id of the column with this header, from the edit page's links. */
    private String itemId(MockHttpSession session, String header) throws Exception {
        String page = mockMvc.perform(get("/repinfo-tools/known").session(session)).andReturn().getResponse()
                .getContentAsString();
        int at = page.indexOf(">" + header + "</a>");
        String before = page.substring(0, at);
        String link = before.substring(before.lastIndexOf("item="));
        return link.substring("item=".length(), link.indexOf('#'));
    }

    @Test
    void describesTestsAndSavesTheColumnsOfAWorkbook() throws Exception {
        MockHttpSession session = loggedIn();
        mockMvc.perform(get("/repinfo-tools").session(session))
                .andExpect(content().string(containsString("Or describe data whose format is already known")));
        mockMvc.perform(post("/repinfo-tools/known/start").param("format", "xlsx").session(session));
        byte[] xlsx = workbook();
        mockMvc.perform(multipart("/repinfo-tools/known/from-sample")
                .file(new MockMultipartFile("sample", "readings.xlsx", "application/octet-stream", xlsx)).session(session));
        mockMvc.perform(get("/repinfo-tools/known").session(session))
                .andExpect(content().string(containsString(">Station</a>")))
                .andExpect(content().string(containsString(">Status</a>")))
                .andExpect(content().string(containsString("PRONOM fmt/214")));

        mockMvc.perform(post("/repinfo-tools/known/items/{id}/update", itemId(session, "Temp")).session(session)
                .param("header", "Temp").param("semanticName", "Air temperature").param("units", "Cel")
                .param("scale", "0.1").param("fillValue", "-9999").param("validMin", "-90").param("validMax", "60"));
        mockMvc.perform(post("/repinfo-tools/known/items/{id}/update", itemId(session, "Status")).session(session)
                .param("header", "Status").param("codes", "1 = good\n2 = suspect"));
        mockMvc.perform(post("/repinfo-tools/known/items/add").param("partId", "x").param("header", "").session(session));

        mockMvc.perform(multipart("/repinfo-tools/known/test")
                        .file(new MockMultipartFile("sample", "readings.xlsx", "application/octet-stream", xlsx))
                        .session(session))
                .andExpect(content().string(containsString("column B")))
                .andExpect(content().string(containsString(" = 20.5 Cel")))
                .andExpect(content().string(containsString(" = 99.9 Cel (outside the valid range)")))
                .andExpect(content().string(containsString("1 value is outside the valid range")))
                .andExpect(content().string(containsString("1 value is the fill value (no data)")))
                .andExpect(content().string(containsString(" = good")))
                .andExpect(content().string(containsString("1 value is not in the code list")));

        String location = mockMvc.perform(post("/repinfo-tools/known/save").session(session))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/resource/");
        String dataObject = archive.decodeId(location.substring("/resource/".length()));
        try {
            store.beginTransaction(ReadWrite.READ);
            try {
                List<Map<String, String>> rows = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?top ?profile ?registry ?software ?semantic WHERE {
                          <%s> im:interpretedUsing ?top .
                          ?top a im:RepInfoAndGroup ; im:hasGroupMember ?profile , ?software , ?semantic ;
                               im:hasStructureRepresentationInformation ?profile ;
                               im:hasOtherRepresentationInformation ?software ;
                               im:hasSemanticRepresentationInformation ?semantic .
                          ?profile a im:FormatProfile ; im:formatRegistryIdentifier ?registry .
                          ?software a im:RepInfoOrGroup .
                        }""".formatted(dataObject));
                assertThat(rows).hasSize(1);
                assertThat(rows.get(0).get("registry")).isEqualTo("PRONOM fmt/214");
                assertThat(rows.get(0).get("software")).isEqualTo(Ns.EX + "software-for-xlsx");
                assertThat(q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?label WHERE { ex:software-for-xlsx im:hasGroupMember/rdfs:label ?label }"""))
                        .extracting(r -> r.get("label")).containsExactlyInAnyOrder("Microsoft Excel",
                                "LibreOffice Calc", "Any application that reads OOXML spreadsheets");
                assertThat(q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?label ?scale WHERE {
                          <%s> im:interpretedUsingRecurse ?sheet .
                          ?sheet bridge:structuralPath "Readings" ; im:interpretedUsingRecurse ?column .
                          ?column bridge:structuralPath "Readings!\\"Temp\\"" ; rdfs:label ?label ; bridge:scaleFactor ?scale .
                        }""".formatted(rows.get(0).get("semantic"))))
                        .containsExactly(Map.of("label", "Air temperature", "scale", "0.1"));
            } finally {
                store.endTransaction(true);
            }
        } finally {
            removeReachable(dataObject);
        }
    }

    @Test
    void readsDelimitedTextByItsProfile() throws Exception {
        MockHttpSession session = loggedIn();
        mockMvc.perform(post("/repinfo-tools/known/start").param("format", "csv").session(session));
        mockMvc.perform(post("/repinfo-tools/known/details").session(session).param("name", "Ledger")
                .param("registryId", "PRONOM x-fmt/18").param("version", "RFC 4180")
                .param("characterEncoding", "ISO-8859-1").param("lineEnding", "LF").param("delimiter", ";")
                .param("quote", "\""));
        byte[] csv = "Café;\"Amount; EUR\"\nA;1,5\n".getBytes(StandardCharsets.ISO_8859_1);
        mockMvc.perform(multipart("/repinfo-tools/known/from-sample")
                .file(new MockMultipartFile("sample", "ledger.csv", "text/csv", csv)).session(session));
        mockMvc.perform(get("/repinfo-tools/known").session(session))
                .andExpect(content().string(containsString(">Café</a>")))
                .andExpect(content().string(containsString(">Amount; EUR</a>")));
        mockMvc.perform(post("/repinfo-tools/known/discard").session(session));
    }

    /** Removes {@code start} and everything reachable from it, except the shared software individuals. */
    private void removeReachable(String start) {
        store.beginTransaction(ReadWrite.WRITE);
        try {
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
        } finally {
            store.endTransaction(true);
        }
    }
}
