package info.oais.infomodel.structure.topcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.util.DataSource;

/**
 * End-to-end tests: Representation Information manifests naming real data
 * and descriptions, through the real DFDL, Kaitai and DRB adapters, into a
 * real STIL {@link StarTable} -- the same {@link OaisStructureTableBuilder#makeStarTable}
 * path TOPCAT itself calls (see {@code run-in-topcat.bat}, alongside these
 * fixtures, for opening them in TOPCAT by hand). The fixtures' file names are
 * arbitrary: every file is named explicitly in a manifest.
 */
class OaisStructureTableBuilderTest {

	private final OaisStructureTableBuilder builder = new OaisStructureTableBuilder();

	private StarTable open(String manifest, String dataObject) throws IOException {
		String location = fixture(manifest).toString() + (dataObject == null ? "" : "#" + dataObject);
		return builder.makeStarTable(DataSource.makeDataSource(location), false, StoragePolicy.PREFER_MEMORY);
	}

	private static Path fixture(String name) {
		try {
			return Path.of(OaisStructureTableBuilderTest.class.getResource("/" + name).toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void readsAPointThroughItsPreferredDescriptionWithUnitsFromItsSemantics() throws IOException {
		StarTable table = open("point-manifest.ttl", null);

		assertColumns(table);
		assertEquals(1, table.getRowCount());
		assertEquals(42, table.getCell(0, 0));
		assertEquals(-7, table.getCell(0, 1));
		assertEquals("hi", table.getCell(0, 2));
		assertEquals("m", table.getColumnInfo(0).getUnitString());
		assertEquals("X ordinate: Distance along the x axis.", table.getColumnInfo(0).getDescription());
	}

	@Test
	void readsAPointThroughAGeneratedKaitaiClass() throws IOException {
		StarTable table = open("kaitai-only-point.ttl", null);

		assertColumns(table);
		assertEquals(42, table.getCell(0, 0));
		assertEquals("hi", table.getCell(0, 2));
	}

	@Test
	void choosesAUsableAlternativeAndTheNamedDataObject() throws IOException {
		StarTable viaDfdl = open("ten-points.ttl", "dfdl-or-drb");
		StarTable viaKaitai = open("ten-points.ttl", "kaitai");
		StarTable viaDrb = open("drb-only-points.ttl", null);

		for (StarTable table : new StarTable[] {viaDfdl, viaKaitai, viaDrb}) {
			assertColumns(table);
			assertEquals(10, table.getRowCount());
			assertEquals(42, table.getCell(0, 0));
			assertEquals("hi", table.getCell(0, 2));
			assertEquals(-500, table.getCell(9, 0));
			assertEquals(500, table.getCell(9, 1));
			assertEquals("deneb", table.getCell(9, 2));
		}
	}

	@Test
	void asksWhichDataObjectWhenAManifestHasSeveral() {
		TableFormatException e = assertThrows(TableFormatException.class, () -> open("ten-points.ttl", null));
		assertTrue(e.getMessage().contains("dfdl-or-drb, kaitai"), e.getMessage());
	}

	@Test
	void declinesWhatIsNotAManifest() {
		assertThrows(TableFormatException.class, () -> open("points.csv", null));
		assertThrows(TableFormatException.class, () -> open("point.dfdl.xsd", null));
	}

	@Test
	void recognisesManifestsByContentNotName() {
		assertTrue(builder.looksLikeFile(fixture("point-manifest.ttl").toString()));
		assertTrue(builder.looksLikeFile(fixture("ten-points.ttl") + "#kaitai"));
		assertFalse(builder.looksLikeFile(fixture("points.csv").toString()));
		assertFalse(builder.looksLikeFile(fixture("point.bin").toString()));
		assertFalse(builder.looksLikeFile("https://example.org/not-local.ttl"));
	}

	private static void assertColumns(StarTable table) {
		assertEquals(3, table.getColumnCount());
		assertEquals("x", table.getColumnInfo(0).getName());
		assertEquals("y", table.getColumnInfo(1).getName());
		assertEquals("label", table.getColumnInfo(2).getName());
		assertEquals(Integer.class, table.getColumnInfo(0).getContentClass());
		assertEquals(String.class, table.getColumnInfo(2).getContentClass());
	}
}
