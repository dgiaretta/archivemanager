package info.oais.infomodel.structure.kaitai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kaitai.struct.ByteBufferKaitaiStream;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.kaitai.generated.Point2d;

/**
 * Exercises {@link KaitaiReflectiveStructureNode#getSourceRange()} against
 * {@code generated/Point2d.java}'s hand-written {@code _debug} map (see that
 * class's Javadoc). This tests the reflective lookup logic in
 * {@link KaitaiReflectiveStructureNode}, not a real {@code ksc --debug}
 * compiler run, which this project could not perform - see the root README.
 */
class KaitaiReflectiveStructureNodeSourceRangeTest {

	@Test
	void reportsPerFieldByteRangesFromTheDebugMap() {
		byte[] bytes = buildPointRecord(7, -3, "hi");
		Point2d point = new Point2d(new ByteBufferKaitaiStream(bytes));
		StructureNode root = KaitaiReflectiveStructureNode.ofRoot("point", point);

		ByteRange xRange = expectRange(root, "x");
		assertEquals(0, xRange.startByteOffset());
		assertEquals(4, xRange.byteLength());

		ByteRange yRange = expectRange(root, "y");
		assertEquals(4, yRange.startByteOffset());
		assertEquals(4, yRange.byteLength());

		ByteRange labelLenRange = expectRange(root, "labelLen");
		assertEquals(8, labelLenRange.startByteOffset());
		assertEquals(1, labelLenRange.byteLength());

		ByteRange labelRange = expectRange(root, "label");
		assertEquals(9, labelRange.startByteOffset());
		assertEquals(2, labelRange.byteLength());

		// The root itself has no enclosing _debug entry describing its own span -
		// see KaitaiReflectiveStructureNode's Javadoc for why that is left empty.
		assertTrue(root.getSourceRange().isEmpty());
	}

	private static ByteRange expectRange(StructureNode root, String childName) {
		Optional<StructureNode> child = root.child(childName);
		assertTrue(child.isPresent(), () -> "expected a child named " + childName);
		Optional<ByteRange> range = child.get().getSourceRange();
		assertTrue(range.isPresent(), () -> childName + " should have a source range");
		return range.get();
	}

	private static byte[] buildPointRecord(int x, int y, String label) {
		byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 1 + labelBytes.length);
		buffer.putInt(x);
		buffer.putInt(y);
		buffer.put((byte) labelBytes.length);
		buffer.put(labelBytes);
		return buffer.array();
	}
}
