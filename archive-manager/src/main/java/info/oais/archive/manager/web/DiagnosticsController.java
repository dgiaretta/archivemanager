package info.oais.archive.manager.web;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A definitive diagnostic for "Dhivehi text shows as question marks":
 * whether that's happening because the *source file* already had the
 * corruption before this app ever read it (the classic Excel "CSV (Comma
 * delimited)" ANSI-code-page export pitfall, or an XLSX that was itself
 * built from an already-corrupted CSV), or because something in this app's
 * own encoding pipeline is at fault.
 *
 * <p>{@link #TEST_STRING} is a Dhivehi phrase hard-coded directly in this
 * Java source file, compiled with UTF-8 source encoding
 * ({@code project.build.sourceEncoding} in pom.xml). If this page renders
 * it correctly, the app's rendering pipeline is fine and the problem is
 * upstream, in the source spreadsheet. If this page *also* shows question
 * marks for a string that never touched any uploaded file at all, the bug
 * is in this app's own pipeline -- most likely the JVM's platform default
 * charset (Java 17 predates JEP 400's "UTF-8 by default"), which can trace
 * back to either the OS's own default (classically Windows, whose ANSI
 * code page has no Thaana representation at all) or simply an unset/non-UTF-8
 * locale environment (LANG/LC_ALL/LC_CTYPE) wherever the process was
 * actually launched from -- this isn't Windows-exclusive; a systemd unit,
 * cron job, or container on Linux doesn't always inherit the same locale
 * an interactive shell has. See the README's internationalisation section.
 */
@Controller
public class DiagnosticsController {

    private final RdfStore store;
    private final QueryRunner q;

    public DiagnosticsController(RdfStore store, QueryRunner q) {
        this.store = store;
        this.q = q;
    }

    /** "Welcome to the National Archives of Maldives" in Dhivehi, mixing Thaana and ASCII digits/punctuation deliberately, to mirror real catalogue data. */
    static final String TEST_STRING = "ދިވެހިރާއްޖޭގެ ގައުމީ އަރުޝީފަށް މަރުހަބާ (NAM, 2026)";

    @GetMapping("/diagnostics/encoding")
    public String encoding(Model model) {
        model.addAttribute("testString", TEST_STRING);
        model.addAttribute("defaultCharset", Charset.defaultCharset().name());
        model.addAttribute("fileEncoding", System.getProperty("file.encoding"));
        model.addAttribute("sunJnuEncoding", System.getProperty("sun.jnu.encoding"));
        model.addAttribute("osName", System.getProperty("os.name"));
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        // Not the JVM's resolved charset, but the underlying locale environment
        // variables that (pre-JEP-400) it's typically derived from -- these are
        // what actually explain *why* the JVM ended up with whatever charset it
        // has, and whether it's unset/non-UTF-8 (LANG=C, LANG unset, etc.) rather
        // than something specific to Windows -- this affects Linux/WSL too if a
        // process was launched in an environment that didn't inherit an
        // interactive shell's locale (a systemd unit, cron, a container).
        model.addAttribute("lang", envOrNotSet("LANG"));
        model.addAttribute("lcAll", envOrNotSet("LC_ALL"));
        model.addAttribute("lcCtype", envOrNotSet("LC_CTYPE"));
        boolean looksOk = "UTF-8".equalsIgnoreCase(Charset.defaultCharset().name());
        model.addAttribute("looksOk", looksOk);
        return "diagnostics/encoding";
    }

    private String envOrNotSet(String name) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? "(not set)" : value;
    }

    /**
     * Mirrors CatalogueImportService.cellToString exactly, so this diagnostic
     * page shows precisely what the real import path would produce, not a
     * different code path that happens to look similar.
     */
    private String cellToString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        return formatter.formatCellValue(cell);
    }

    /**
     * Tests the one link in the chain none of the other diagnostics on this
     * page actually exercise: Jena/TDB2's own storage round-trip. The
     * hardcoded test string above never touches TDB2 at all (it goes
     * straight from a Java constant to the rendered page); the XLSX read
     * test doesn't either (it shows what POI extracted, nothing more). If
     * both of those pass but Dhivehi text still corrupts after a real
     * import, this is the remaining untested step: does the exact same
     * string survive being written to the data graph, committed, and read
     * back in a later, separate request?
     *
     * <p>Writes to a single fixed, well-known test resource
     * ({@link #ROUNDTRIP_TEST_IRI}) so repeated tests just overwrite it
     * rather than accumulating test junk in the data graph. The write
     * happens in this POST handler's transaction; the read happens in the
     * separate GET request the redirect triggers, deliberately a different
     * transaction, since that's what actually happens in the real
     * import-then-later-browse scenario this is meant to simulate.
     */
    private static final String ROUNDTRIP_TEST_IRI = "https://oais.info/nam#diagnostic-test-string";

    @GetMapping("/diagnostics/tdb2-roundtrip")
    public String roundtripForm(Model model) {
        String sparql = """
                SELECT ?value WHERE {
                  <%s> <http://www.w3.org/2000/01/rdf-schema#comment> ?value .
                }
                """.formatted(ROUNDTRIP_TEST_IRI);
        List<Map<String, String>> rows = q.select(store.dataModel(), sparql);
        model.addAttribute("storedValue", rows.isEmpty() ? null : rows.get(0).get("value"));
        model.addAttribute("expectedValue", TEST_STRING);
        return "diagnostics/tdb2-roundtrip";
    }

    @PostMapping("/diagnostics/tdb2-roundtrip")
    public String roundtripWrite() {
        org.apache.jena.rdf.model.Model m = store.dataModel();
        Resource resource = m.createResource(ROUNDTRIP_TEST_IRI);
        m.removeAll(resource, RDFS.comment, null);
        resource.addProperty(RDFS.comment, TEST_STRING);
        return "redirect:/diagnostics/tdb2-roundtrip";
    }

    /**
     * Upload the actual problematic .xlsx here and see exactly what Apache
     * POI extracts from its first few rows -- no Jena, no data graph, no
     * rendering pipeline involved at all, just "read the file, show what
     * came out" using the identical reading code
     * {@code CatalogueImportService.importXlsx} uses. This isolates whether
     * corruption is present the *moment* POI reads the file (a genuine
     * library/file-format issue, not an environment one) versus being
     * introduced somewhere later (storage, rendering) -- which the plain
     * hard-coded test string on /diagnostics/encoding can't distinguish,
     * since it never goes through file reading at all. Admin-gated, same as
     * every other upload endpoint, since it accepts a file.
     */
    @GetMapping("/diagnostics/xlsx-test")
    public String xlsxTestForm() {
        return "diagnostics/xlsx-test";
    }

    @PostMapping("/diagnostics/xlsx-test")
    public String xlsxTest(@RequestParam("file") MultipartFile file, Model model) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Choose an .xlsx file first.");
            return "diagnostics/xlsx-test";
        }
        List<List<String>> rows = new ArrayList<>();
        try (var in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int firstRow = sheet.getFirstRowNum();
            int lastRow = Math.min(sheet.getLastRowNum(), firstRow + 4); // header + up to 4 data rows
            for (int r = firstRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells.add(cellToString(cell, formatter));
                    }
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            model.addAttribute("error", "Could not read that file: " + e.getMessage());
            return "diagnostics/xlsx-test";
        }
        model.addAttribute("rows", rows);
        return "diagnostics/xlsx-test";
    }
}
