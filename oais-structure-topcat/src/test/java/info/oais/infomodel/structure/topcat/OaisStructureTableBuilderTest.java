package info.oais.infomodel.structure.topcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.util.FileDataSource;

/**
 * End-to-end tests against this project's own demo fixtures: real bytes,
 * through the real DFDL and Kaitai adapters, into a real STIL
 * {@link StarTable}. No TOPCAT installation needed for this -- it exercises
 * exactly the same {@link OaisStructureTableBuilder#makeStarTable} path
 * TOPCAT itself would call (see {@code run-in-topcat.bat}, alongside these
 * fixtures, for actually opening them in TOPCAT by hand).
 *
 * <p>{@code point.bin} (single row, x=42/y=-7/label="hi" -- copied from
 * {@code oais-structure-demo/src/main/resources/point.bin}) covers the
 * single-record case; {@code points.csv}/{@code points-kaitai.csv} (ten
 * rows, deliberately scoped to this module rather than editing the shared
 * {@code points.csv} other modules' tests already assert exact row content
 * against) cover the repeated-row case, via both engines.</p>
 */
class OaisStructureTableBuilderTest {

    private static final String POINT_VIEW_XML = "/point-table-view.xml";
    private static final String POINT_BIN = "/point.bin";
    private static final String POINT_DFDL_XSD = "/point.dfdl.xsd";
    private static final String KAITAI_GENERATED_CLASS =
            "info.oais.infomodel.structure.kaitai.generated.Point2d";

    private static final String POINTS_CSV = "/points.csv";
    private static final String POINTS_DFDL_XSD = "/points.dfdl.xsd";
    private static final String POINTS_VIEW_XML = "/points-table-view.xml";
    private static final String POINTS_KAITAI_VIEW_XML = "/points-kaitai-table-view.xml";
    private static final String CSV_POINTS_KAITAI_GENERATED_CLASS =
            "info.oais.infomodel.structure.kaitai.generated.CsvPoints";

    private final OaisStructureTableBuilder builder = new OaisStructureTableBuilder();

    @TempDir
    Path tempDir;

    private Path dataPath;

    @BeforeEach
    void copyDataAndViewSidecar() throws IOException {
        dataPath = tempDir.resolve("point.bin");
        copyResource(POINT_BIN, dataPath);
        copyResource(POINT_VIEW_XML, tempDir.resolve("point-table-view.xml"));
    }

    @Test
    void readsDfdlDescribedPointAsATable() throws IOException {
        copyResource(POINT_DFDL_XSD, tempDir.resolve("point.dfdl.xsd"));

        StarTable table = builder.makeStarTable(
                new FileDataSource(dataPath.toFile()), false, StoragePolicy.PREFER_MEMORY);

        assertColumns(table);
        assertEquals(1, table.getRowCount());
        assertEquals(42, table.getCell(0, 0));
        assertEquals(-7, table.getCell(0, 1));
        assertEquals("hi", table.getCell(0, 2));
    }

    @Test
    void readsKaitaiDescribedPointAsATable() throws IOException {
        Files.writeString(tempDir.resolve("point.ksy.classname"), KAITAI_GENERATED_CLASS, StandardCharsets.UTF_8);

        StarTable table = builder.makeStarTable(
                new FileDataSource(dataPath.toFile()), false, StoragePolicy.PREFER_MEMORY);

        assertColumns(table);
        assertEquals(1, table.getRowCount());
        assertEquals(42, table.getCell(0, 0));
        assertEquals(-7, table.getCell(0, 1));
        assertEquals("hi", table.getCell(0, 2));
    }

    @Test
    void readsDfdlDescribedTenRowCsvAsATable() throws IOException {
        Path csvPath = tempDir.resolve("points.csv");
        copyResource(POINTS_CSV, csvPath);
        copyResource(POINTS_DFDL_XSD, tempDir.resolve("points.dfdl.xsd"));
        copyResource(POINTS_VIEW_XML, tempDir.resolve("points-table-view.xml"));

        StarTable table = builder.makeStarTable(
                new FileDataSource(csvPath.toFile()), false, StoragePolicy.PREFER_MEMORY);

        assertColumns(table);
        assertEquals(10, table.getRowCount());
        assertEquals(42, table.getCell(0, 0));
        assertEquals("hi", table.getCell(0, 2));
        assertEquals(-500, table.getCell(9, 0));
        assertEquals(500, table.getCell(9, 1));
        assertEquals("deneb", table.getCell(9, 2));
    }

    @Test
    void readsKaitaiDescribedTenRowCsvAsATable() throws IOException {
        Path csvPath = tempDir.resolve("points-kaitai.csv");
        copyResource("/points-kaitai.csv", csvPath);
        Files.writeString(tempDir.resolve("points-kaitai.ksy.classname"), CSV_POINTS_KAITAI_GENERATED_CLASS,
                StandardCharsets.UTF_8);
        copyResource(POINTS_KAITAI_VIEW_XML, tempDir.resolve("points-kaitai-table-view.xml"));

        StarTable table = builder.makeStarTable(
                new FileDataSource(csvPath.toFile()), false, StoragePolicy.PREFER_MEMORY);

        assertColumns(table);
        assertEquals(10, table.getRowCount());
        assertEquals(42, table.getCell(0, 0));
        assertEquals("hi", table.getCell(0, 2));
        assertEquals(-500, table.getCell(9, 0));
        assertEquals(500, table.getCell(9, 1));
        assertEquals("deneb", table.getCell(9, 2));
    }

    @Test
    void declinesAFileWithNoFormatSidecar() {
        // Only the view sidecar was copied in @BeforeEach -- no .dfdl.xsd,
        // .ksy.classname or .drb(.properties), so this builder shouldn't
        // claim it.
        assertThrows(TableFormatException.class, () ->
                builder.makeStarTable(new FileDataSource(dataPath.toFile()), false, StoragePolicy.PREFER_MEMORY));
    }

    @Test
    void looksLikeFileAgreesWithMakeStarTable() throws IOException {
        assertTrue(!builder.looksLikeFile(dataPath.toString()), "no format sidecar yet");

        copyResource(POINT_DFDL_XSD, tempDir.resolve("point.dfdl.xsd"));
        assertTrue(builder.looksLikeFile(dataPath.toString()), "DFDL sidecar now present");
    }

    private void assertColumns(StarTable table) {
        assertEquals(3, table.getColumnCount());
        assertEquals("x", table.getColumnInfo(0).getName());
        assertEquals("y", table.getColumnInfo(1).getName());
        assertEquals("label", table.getColumnInfo(2).getName());
        assertEquals(Integer.class, table.getColumnInfo(0).getContentClass());
        assertEquals(String.class, table.getColumnInfo(2).getContentClass());
    }

    private static void copyResource(String resourcePath, Path destination) throws IOException {
        try (InputStream in = OaisStructureTableBuilderTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Test fixture not found on classpath: " + resourcePath);
            }
            Files.copy(in, destination);
        }
    }
}
