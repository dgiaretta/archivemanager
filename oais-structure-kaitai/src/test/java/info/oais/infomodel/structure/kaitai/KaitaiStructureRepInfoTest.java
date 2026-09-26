package info.oais.infomodel.structure.kaitai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.kaitai.generated.Point2d;

class KaitaiStructureRepInfoTest {

	@Test
	void parsesAHandBuiltPointRecordGenerically() throws Exception {
		byte[] bytes = pointBytes(42, -7, "hi");
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		KaitaiFormatSpecification spec = new KaitaiFormatSpecification(Point2d.class);
		KaitaiStructureRepInfo structureRepInfo = new KaitaiStructureRepInfo(spec);

		StructureNode point = structureRepInfo.apply(digitalObject);

		assertEquals(42, point.valueAt("x").orElseThrow());
		assertEquals(-7, point.valueAt("y").orElseThrow());
		assertEquals("hi", point.valueAt("label").orElseThrow());
		assertEquals(2, point.valueAt("labelLen").orElseThrow());
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
