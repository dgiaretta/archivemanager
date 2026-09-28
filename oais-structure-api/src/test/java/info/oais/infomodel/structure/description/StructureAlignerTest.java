package info.oais.infomodel.structure.description;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Aligns hand-built trees in the two shapes the engines really produce for
 * the reference packet (see the A1 spike): DFDL/DRB/drb-python's, and Kaitai
 * Struct's. Both must come out as the same canonical lines.
 */
class StructureAlignerTest {

	private static final List<String> EXPECTED = List.of(
			"packet.version = 3",
			"packet.n = 2",
			"packet.name_len = 2",
			"packet.name = ab",
			"packet.record[0].kind = 1",
			"packet.record[0].body -> temp_body",
			"packet.record[0].body.temp_body.temp = 21.5",
			"packet.record[0].flags = 1",
			"packet.record[0].extra = 300",
			"packet.record[1].kind = 2",
			"packet.record[1].body -> label_body",
			"packet.record[1].body.label_body.label_len = 2",
			"packet.record[1].body.label_body.label = hi",
			"packet.record[1].flags = 0",
			"packet.record[1].extra = <absent>",
			"packet.pad = 0xaabbcc",
			"packet.trailer[0] = 7",
			"packet.trailer[1] = 8",
			"packet.trailer[2] = 9");

	@Test
	void alignsTheDfdlDrbShape() {
		StructureNode decoded = composite("packet",
				leaf("version", (short) 3), leaf("n", 2), leaf("name_len", (short) 2), leaf("name", "ab"),
				composite("record", leaf("kind", (short) 1),
						composite("body", composite("temp_body", leaf("temp", 21.5f))),
						leaf("flags", (short) 1), leaf("extra", 300)),
				composite("record", leaf("kind", (short) 2),
						composite("body", composite("label_body", leaf("label_len", 2L), leaf("label", "hi\u0000"))),
						leaf("flags", (short) 0)),
				// DRB's run of single bytes for raw bytes
				leaf("pad", 0xAAL), leaf("pad", 0xBBL), leaf("pad", 0xCCL),
				leaf("trailer", (short) 7), leaf("trailer", (short) 8), leaf("trailer", (short) 9));

		AlignedNode aligned = StructureAligner.align(PacketFormat.description(), decoded);

		assertEquals(EXPECTED, aligned.canonicalLines());
	}

	@Test
	void alignsTheKaitaiShape() {
		StructureNode decoded = composite("root",
				leaf("version", 3), leaf("n", 2), leaf("nameLen", 2), leaf("name", "ab"),
				array("record",
						composite("0", leaf("kind", 1), composite("body", leaf("temp", 21.5f)),
								leaf("flags", 1), leaf("extra", 300)),
						composite("1", leaf("kind", 2), composite("body", leaf("labelLen", 2), leaf("label", "hi")),
								leaf("flags", 0), DefaultStructureNode.builder("extra", StructureNodeKind.LEAF).build())),
				leaf("pad", new byte[] {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}),
				array("trailer", leaf("0", 7), leaf("1", 8), leaf("2", 9)));

		AlignedNode aligned = StructureAligner.align(PacketFormat.description(), decoded);

		assertEquals(EXPECTED, aligned.canonicalLines());
	}

	@Test
	void carriesSemanticsThroughToTheDecodedValues() {
		StructureNode decoded = composite("packet",
				leaf("version", 3), leaf("n", 1), leaf("name_len", 0), leaf("name", ""),
				composite("record", leaf("kind", 1), composite("body", composite("temp_body", leaf("temp", 21.5))),
						leaf("flags", 0)),
				leaf("pad", new byte[] {0}));

		AlignedNode record = StructureAligner.align(PacketFormat.description(), decoded).children().get(4);
		AlignedNode kind = record.children().get(0);
		AlignedNode temp = record.children().get(1).children().get(0).children().get(0);

		assertEquals("temperature", kind.meaning().orElseThrow());
		assertEquals("Temperature", temp.semantics().semanticName());
		assertEquals("degC", temp.semantics().units());
		assertEquals(AlignedNode.Presence.ABSENT, record.children().get(3).presence());
		assertTrue(record.children().get(3).description() instanceof FieldDescription);
	}

	private static StructureNode leaf(String name, Object value) {
		return DefaultStructureNode.leaf(name, value);
	}

	private static StructureNode composite(String name, StructureNode... children) {
		return DefaultStructureNode.builder(name, StructureNodeKind.COMPOSITE).addChildren(List.of(children)).build();
	}

	private static StructureNode array(String name, StructureNode... children) {
		return DefaultStructureNode.builder(name, StructureNodeKind.ARRAY).addChildren(List.of(children)).build();
	}
}
