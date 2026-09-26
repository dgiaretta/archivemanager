package info.oais.infomodel.structure.kaitai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.kaitai.generated.CsvPoints;
import info.oais.infomodel.structure.kaitai.generated.Point2d;

/**
 * Exercises the Kaitai Struct adapter against {@link CsvPoints} - a
 * {@code repeat: eos} format, unlike {@link Point2d}'s single fixed-width
 * record - proving {@link KaitaiReflectiveStructureNode} reports the
 * repeated {@code rows} field as {@link StructureNodeKind#ARRAY} generically,
 * with no CSV-specific adapter code.
 */
class CsvPointsStructureRepInfoTest {

	@Test
	void parsesRepeatedCsvRowsAsAnArray() throws Exception {
		byte[] bytes = ("42,-7,hi\n" + "7,13,demo\n").getBytes(StandardCharsets.US_ASCII);
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		KaitaiFormatSpecification spec = new KaitaiFormatSpecification(CsvPoints.class);
		KaitaiStructureRepInfo structureRepInfo = new KaitaiStructureRepInfo(spec);

		StructureNode csv = structureRepInfo.apply(digitalObject);

		StructureNode rows = csv.child("rows").orElseThrow();
		assertEquals(StructureNodeKind.ARRAY, rows.getKind());
		assertEquals(2, rows.getChildren().size());

		assertEquals("42", csv.valueAt("rows", "0", "x").orElseThrow());
		assertEquals("-7", csv.valueAt("rows", "0", "y").orElseThrow());
		assertEquals("hi", csv.valueAt("rows", "0", "label").orElseThrow());
		assertEquals("7", csv.valueAt("rows", "1", "x").orElseThrow());
		assertEquals("13", csv.valueAt("rows", "1", "y").orElseThrow());
		assertEquals("demo", csv.valueAt("rows", "1", "label").orElseThrow());
	}

	@Test
	void lastLineNeedsNoTrailingNewline() throws Exception {
		byte[] bytes = "1,2,only".getBytes(StandardCharsets.US_ASCII);
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		StructureNode csv = new KaitaiStructureRepInfo(new KaitaiFormatSpecification(CsvPoints.class))
				.apply(digitalObject);

		assertEquals("only", csv.valueAt("rows", "0", "label").orElseThrow());
	}
}
