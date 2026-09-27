package info.oais.infomodel.structure.drb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureInterpreterFactory;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Exercises {@link DrbStructureRepInfo} against the real DRB 2.5.13 library
 * (a test dependency of this module), with SDF schemas in
 * {@code src/test/resources} and hand-built bytes.
 */
class DrbStructureRepInfoTest {

	@Test
	void decodesALittleEndianBinaryRecordWithAnSdfSchema() throws Exception {
		byte[] bytes = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(42).putInt(-7).putDouble(2.5).put("abcd".getBytes(StandardCharsets.US_ASCII)).array();

		StructureNode point = apply("/point-le.drb.xsd", bytes);

		assertEquals("point", point.getName());
		assertEquals(42L, point.valueAt("x").orElseThrow());
		assertEquals(-7L, point.valueAt("y").orElseThrow());
		assertEquals(2.5, point.valueAt("z").orElseThrow());
		assertEquals("abcd", point.valueAt("label").orElseThrow());
		assertEquals("x ordinate", point.child("x").orElseThrow().getAttributes().get("documentation"));
	}

	@Test
	void surfacesRepeatedCsvRowsAsSameNamedSiblingsWithByteRanges() throws Exception {
		StructureNode points = apply("/csv-points.drb.xsd", "42,-7,hi\n7,13,demo\n".getBytes(StandardCharsets.US_ASCII));

		List<StructureNode> rows = points.childrenNamed("row");
		assertEquals(2, rows.size());
		assertEquals(StructureNodeKind.COMPOSITE, rows.get(0).getKind());
		assertEquals(42L, rows.get(0).valueAt("x").orElseThrow());
		assertEquals("hi", rows.get(0).valueAt("label").orElseThrow());
		assertEquals(13L, rows.get(1).valueAt("y").orElseThrow());
		assertEquals("demo", rows.get(1).valueAt("label").orElseThrow());
		assertEquals(ByteRange.ofBytes(9, 2), rows.get(1).child("x").orElseThrow().getSourceRange().orElseThrow());
	}

	@Test
	void recognisesAnXmlDocumentWithoutASchema() {
		String xml = "<catalogue><item id=\"1\">first</item><item id=\"2\">second</item></catalogue>";

		StructureNode root = new DrbStructureRepInfo(DrbFormatSpecification.autoDetect("xml"))
				.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

		StructureNode catalogue = root.getName().equals("catalogue") ? root : root.child("catalogue").orElseThrow();
		assertEquals(2, catalogue.childrenNamed("item").size());
		assertEquals("second", catalogue.childrenNamed("item").get(1).getValue().orElseThrow());
	}

	@Test
	void isFoundThroughTheServiceLoader() {
		assertTrue(new StructureInterpreterFactory().availableLanguages()
				.contains(info.oais.infomodel.structure.SpecificationLanguage.DRB));
	}

	@Test
	void reportsAFailureWhenTheDataIsTooShortForTheSchema() {
		assertThrows(StructureInterpretationException.class, () -> apply("/point-le.drb.xsd", new byte[3]));
	}

	private StructureNode apply(String schemaResource, byte[] bytes) throws Exception {
		DrbFormatSpecification spec = new DrbFormatSpecification(getClass().getResource(schemaResource).toURI());
		return new DrbStructureRepInfo(spec).apply(new DigitalObjectRefImpl(new ByteArrayInputStream(bytes)));
	}
}
