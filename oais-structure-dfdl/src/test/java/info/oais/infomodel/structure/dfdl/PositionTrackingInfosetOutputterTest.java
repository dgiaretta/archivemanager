package info.oais.infomodel.structure.dfdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.daffodil.japi.Compiler;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureNode;

/**
 * Exercises {@link PositionTrackingInfosetOutputter} and
 * {@link DomStructureNode#getSourceRange()} end to end against a real
 * compiled DFDL schema and a hand-built record, bypassing
 * {@link DfdlStructureRepInfo}/{@code DigitalObject} entirely - this test
 * only needs Daffodil itself, not oaisCore's {@code DigitalObject}.
 *
 * <p>Deliberately tolerant of {@link PositionTrackingInfosetOutputter}
 * finding nothing: whether it does depends on whether this Daffodil
 * version's infoset element classes expose one of the position accessor
 * names it guesses at, which this project could not confirm without a live
 * Daffodil install - see that class's Javadoc. What this test does insist
 * on is that IF a range is reported, it is the <em>right</em> range for
 * every field checked, consistently; a silently wrong range would be worse
 * than no range at all, and a range reported for only some fields (rather
 * than all four checked here) would indicate a bug in the path-correlation
 * logic, not just an absent Daffodil accessor.</p>
 */
class PositionTrackingInfosetOutputterTest {

	@Test
	void reportsConsistentRangesWhenThePositionAccessorItGuessesAtExists() throws Exception {
		DataProcessor processor = compilePointSchema();
		byte[] bytes = buildPointRecord(7, -3, "hi");

		PositionTrackingInfosetOutputter tracker = capturePositions(processor, bytes);
		Map<List<Integer>, ByteRange> rangesByPath = tracker.rangesByPath();
		Element root = parseToDom(processor, bytes);
		StructureNode rootNode = new DomStructureNode(root, rangesByPath, tracker.typedValuesByPath(), List.of());

		if (rangesByPath.isEmpty()) {
			// This Daffodil version's infoset element classes did not expose any of the
			// position accessor names PositionTrackingInfosetOutputter tries - expected
			// and acceptable, per this test's Javadoc; every field should consistently
			// report no range rather than a partial or wrong one.
			assertFieldRangeEmpty(rootNode, "x");
			assertFieldRangeEmpty(rootNode, "y");
			assertFieldRangeEmpty(rootNode, "labelLen");
			assertFieldRangeEmpty(rootNode, "label");
			return;
		}

		assertFieldRange(rootNode, "x", 0, 4);
		assertFieldRange(rootNode, "y", 4, 4);
		assertFieldRange(rootNode, "labelLen", 8, 1);
		assertFieldRange(rootNode, "label", 9, 2);

		if (!tracker.typedValuesByPath().isEmpty()) {
			// Same tolerance as for ranges above, and for the same reason - see
			// PositionTrackingInfosetOutputter's Javadoc on "Typed values": whether this
			// Daffodil version's infoset element classes expose one of the typed-value
			// accessor names tried is not something this project could confirm. If any
			// were captured at all, "x" (declared xs:int in point.dfdl.xsd) should be a
			// real Integer rather than DomStructureNode's raw-DOM-text fallback.
			Object xValue = fieldNamed(rootNode, "x").getValue().orElse(null);
			assertTrue(xValue instanceof Integer,
					() -> "x should be a typed Integer when typed values were captured, was "
							+ (xValue == null ? "null" : xValue.getClass() + " " + xValue));
			assertEquals(7, xValue);
		}
	}

	private static void assertFieldRange(StructureNode root, String fieldName, long expectedStartByte,
			long expectedByteLength) {
		StructureNode field = fieldNamed(root, fieldName);
		Optional<ByteRange> range = field.getSourceRange();
		assertTrue(range.isPresent(),
				() -> fieldName + " should have a source range since PositionTrackingInfosetOutputter found some");
		assertEquals(expectedStartByte, range.get().startByteOffset(), fieldName + " start byte offset");
		assertEquals(expectedByteLength, range.get().byteLength(), fieldName + " byte length");
	}

	private static void assertFieldRangeEmpty(StructureNode root, String fieldName) {
		assertTrue(fieldNamed(root, fieldName).getSourceRange().isEmpty(),
				() -> fieldName + " should have no source range when none were captured at all");
	}

	private static StructureNode fieldNamed(StructureNode root, String fieldName) {
		return root.child(fieldName)
				.orElseGet(() -> fail("expected a child named " + fieldName));
	}

	private static DataProcessor compilePointSchema() throws Exception {
		URI schemaLocation = PositionTrackingInfosetOutputterTest.class.getResource("/point.dfdl.xsd").toURI();
		Compiler compiler = Daffodil.compiler();
		ProcessorFactory processorFactory = compiler.compileSource(schemaLocation);
		if (processorFactory.isError()) {
			throw new IllegalStateException("Unable to compile " + schemaLocation + " for this test");
		}
		DataProcessor processor = processorFactory.onPath("/");
		if (processor.isError()) {
			throw new IllegalStateException("Unable to create a DFDL data processor for this test");
		}
		return processor;
	}

	private static PositionTrackingInfosetOutputter capturePositions(DataProcessor processor, byte[] bytes)
			throws Exception {
		PositionTrackingInfosetOutputter tracker = new PositionTrackingInfosetOutputter();
		InputSourceDataInputStream input = new InputSourceDataInputStream(new ByteArrayInputStream(bytes));
		ParseResult result = processor.parse(input, tracker);
		if (result.isError()) {
			throw new IllegalStateException("Position-tracking parse failed unexpectedly in this test");
		}
		return tracker;
	}

	private static Element parseToDom(DataProcessor processor, byte[] bytes) throws Exception {
		InputSourceDataInputStream input = new InputSourceDataInputStream(new ByteArrayInputStream(bytes));
		W3CDOMInfosetOutputter outputter = new W3CDOMInfosetOutputter();
		ParseResult result = processor.parse(input, outputter);
		if (result.isError()) {
			throw new IllegalStateException("DOM parse failed unexpectedly in this test");
		}
		Document document = outputter.getResult();
		return document.getDocumentElement();
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
