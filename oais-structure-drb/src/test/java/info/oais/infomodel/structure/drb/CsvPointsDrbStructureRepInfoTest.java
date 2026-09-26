package info.oais.infomodel.structure.drb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.drb.fakedrb.FakeCsvDrbFactoryResolver;

/**
 * Exercises the reflective bridge against {@link FakeCsvDrbFactoryResolver} -
 * a repeated, delimited-text format, unlike {@link DrbStructureRepInfoTest}'s
 * single {@code x=...;y=...;label=...} record - proving repetition surfaces
 * as repeated same-named {@code row} children (the DFDL/DRB convention), not
 * a Kaitai-style {@code ARRAY} node.
 */
class CsvPointsDrbStructureRepInfoTest {

	@Test
	void bridgesRepeatedCsvRowsAsSameNamedSiblings() {
		String text = "42,-7,hi\n7,13,demo\n";
		DigitalObject digitalObject = new DigitalObjectRefImpl(
				new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

		DrbFormatSpecification spec = new DrbFormatSpecification(
				FakeCsvDrbFactoryResolver.class.getName(), null, null);
		DrbStructureRepInfo structureRepInfo = new DrbStructureRepInfo(spec);

		StructureNode rows = structureRepInfo.apply(digitalObject);

		assertEquals(2, rows.childrenNamed("row").size());
		assertEquals("42", rows.childrenNamed("row").get(0).valueAt("x").orElseThrow());
		assertEquals("-7", rows.childrenNamed("row").get(0).valueAt("y").orElseThrow());
		assertEquals("hi", rows.childrenNamed("row").get(0).valueAt("label").orElseThrow());
		assertEquals("7", rows.childrenNamed("row").get(1).valueAt("x").orElseThrow());
		assertEquals("13", rows.childrenNamed("row").get(1).valueAt("y").orElseThrow());
		assertEquals("demo", rows.childrenNamed("row").get(1).valueAt("label").orElseThrow());
	}
}
