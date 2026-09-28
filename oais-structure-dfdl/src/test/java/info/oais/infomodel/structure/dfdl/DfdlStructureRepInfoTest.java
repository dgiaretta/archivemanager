package info.oais.infomodel.structure.dfdl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.ElementPath;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;

/**
 * Exercises {@link DfdlStructureRepInfo} against {@code point.dfdl.xsd}
 * using hand-built bytes for the same little "point" format used by the
 * Kaitai Struct adapter's tests, so the two can be compared.
 *
 * <p>Requires a working Daffodil (daffodil-japi) on the test classpath; see
 * the module and root READMEs for why that could not be verified inside the
 * sandbox this project was originally authored in.</p>
 */
class DfdlStructureRepInfoTest {

	@Test
	void parsesAHandBuiltPointRecord() throws Exception {
		byte[] bytes = pointBytes(42, -7, "hi");

		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));
		DfdlFormatSpecification spec = new DfdlFormatSpecification(
				getClass().getResource("/point.dfdl.xsd").toURI());
		DfdlStructureRepInfo structureRepInfo = new DfdlStructureRepInfo(spec);

		StructureNode point = structureRepInfo.apply(digitalObject);

		assertEquals("42", point.valueAt("x").orElseThrow().toString());
		assertEquals("-7", point.valueAt("y").orElseThrow().toString());
		assertEquals("hi", point.valueAt("label").orElseThrow().toString());
	}

	@Test
	void writesAPointRecordBackUnchangedAndChanged() throws Exception {
		DfdlStructureRepInfo structureRepInfo = new DfdlStructureRepInfo(new DfdlFormatSpecification(
				getClass().getResource("/point.dfdl.xsd").toURI()));
		byte[] bytes = pointBytes(42, -7, "hi");

		assertTrue(structureRepInfo.roundTrip(new DigitalObjectRefImpl(new ByteArrayInputStream(bytes))).identical());

		byte[] changed = structureRepInfo.write(new DigitalObjectRefImpl(new ByteArrayInputStream(bytes)),
				Map.of(ElementPath.parse("/y"), "1000", ElementPath.parse("/label"), "ok"));
		assertArrayEquals(pointBytes(42, 1000, "ok"), changed);

		StructureInterpretationException missing = assertThrows(StructureInterpretationException.class,
				() -> structureRepInfo.write(new DigitalObjectRefImpl(new ByteArrayInputStream(bytes)),
						Map.of(ElementPath.parse("/z"), "1")));
		assertEquals("There's no element /z: nothing is called 'z' there", missing.getMessage());
	}

	private static byte[] pointBytes(int x, int y, String label) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(x);
			out.writeInt(y);
			byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
			out.writeByte(labelBytes.length);
			out.write(labelBytes);
		}
		return bytes.toByteArray();
	}
}
