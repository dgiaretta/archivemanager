package info.oais.infomodel.structure.dfdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.StructureNode;

/**
 * Exercises {@link DfdlStructureRepInfo} against {@code csv-points.dfdl.xsd}
 * - a repeated, delimited-text format, unlike {@code point.dfdl.xsd}'s
 * single fixed-width binary record.
 */
class CsvPointsDfdlStructureRepInfoTest {

	@Test
	void parsesRepeatedCsvRowsAsSameNamedSiblings() throws Exception {
		byte[] bytes = "42,-7,hi\n7,13,demo".getBytes(StandardCharsets.US_ASCII);
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		DfdlFormatSpecification spec = new DfdlFormatSpecification(
				getClass().getResource("/csv-points.dfdl.xsd").toURI());
		DfdlStructureRepInfo structureRepInfo = new DfdlStructureRepInfo(spec);

		StructureNode rows = structureRepInfo.apply(digitalObject);

		assertEquals(2, rows.childrenNamed("row").size());
		assertEquals("42", rows.childrenNamed("row").get(0).valueAt("x").orElseThrow().toString());
		assertEquals("-7", rows.childrenNamed("row").get(0).valueAt("y").orElseThrow().toString());
		assertEquals("hi", rows.childrenNamed("row").get(0).valueAt("label").orElseThrow().toString());
		assertEquals("7", rows.childrenNamed("row").get(1).valueAt("x").orElseThrow().toString());
		assertEquals("13", rows.childrenNamed("row").get(1).valueAt("y").orElseThrow().toString());
		assertEquals("demo", rows.childrenNamed("row").get(1).valueAt("label").orElseThrow().toString());
	}

	@Test
	void toleratesATrailingNewlineAfterTheLastRow() throws Exception {
		// Real CSV files usually end with a trailing newline; the "rows" sequence's
		// %NL; separator is infix (between rows, not after each one), so this must
		// still parse as exactly two rows, not a spurious empty third one.
		byte[] bytes = "42,-7,hi\n7,13,demo\n".getBytes(StandardCharsets.US_ASCII);
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		DfdlFormatSpecification spec = new DfdlFormatSpecification(
				getClass().getResource("/csv-points.dfdl.xsd").toURI());
		StructureNode rows = new DfdlStructureRepInfo(spec).apply(digitalObject);

		assertEquals(2, rows.childrenNamed("row").size());
	}
}
