package info.oais.infomodel.structure.dfdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
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
