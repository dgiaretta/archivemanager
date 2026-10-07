package info.oais.infomodel.structure.east;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

/**
 * {@link EastWriter}: what it writes reads back with {@link EastReader} and
 * decodes the same data with {@link EastInterpreter}.
 */
class EastWriterTest {

	private static FieldDescription field(String name, PrimitiveType type, String length) {
		return new FieldDescription(name, name, type, length == null ? null : Expression.parse(length), null,
				Occurrence.ONCE, Semantics.NONE);
	}

	/** Counts, lengths, a choice, an optional field, codes, a range, a little-endian field, and a trailer to the end. */
	static FormatDescription packet() {
		Map<String, String> codes = new LinkedHashMap<>();
		codes.put("1", "temperature");
		codes.put("2", "label");
		FieldDescription kind = field("kind", PrimitiveType.UINT8, null)
				.withSemantics(new Semantics("Kind", "What the record holds", null, null, null, codes, null, null, null,
						null, null));
		RecordDescription temp = RecordDescription.of("temp_body", List.of(
				new FieldDescription("temp", "temp", PrimitiveType.FLOAT32, null, ByteOrder.LITTLE_ENDIAN,
						Occurrence.ONCE, Semantics.of("Temperature", "Sensor temperature", "degC"))));
		RecordDescription label = RecordDescription.of("label_body", List.of(
				field("label_len", PrimitiveType.UINT8, null), field("label", PrimitiveType.STRING, "label_len")));
		ChoiceDescription body = new ChoiceDescription("body", "body", Expression.parse("kind"),
				List.of(new ChoiceDescription.Branch("1", temp), new ChoiceDescription.Branch("2", label)),
				Occurrence.ONCE, Semantics.NONE);
		RecordDescription record = new RecordDescription("record", "record", List.of(kind, body,
				field("flags", PrimitiveType.UINT8, null).withSemantics(new Semantics(null, null, null, null, null,
						Map.of(), null, null, null, new BigDecimal(0), new BigDecimal(1))),
				field("extra", PrimitiveType.UINT16, null)
						.withOccurrence(new Occurrence.Optional(Expression.parse("flags = 1")))),
				null, new Occurrence.Repeated(Expression.parse("n")), Semantics.NONE);
		RecordDescription root = new RecordDescription("packet", "packet", List.of(
				field("version", PrimitiveType.UINT8, null), field("n", PrimitiveType.UINT16, null),
				field("name_len", PrimitiveType.UINT8, null), field("name", PrimitiveType.STRING, "name_len"),
				record, field("pad", PrimitiveType.BYTES, "n * 2 - 1"),
				field("trailer", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.UntilEnd())),
				null, Occurrence.ONCE, Semantics.NONE);
		return new FormatDescription("packet", "A test packet.", ByteOrder.BIG_ENDIAN, List.of("pkt"), root);
	}

	static byte[] packetBytes() {
		ByteBuffer b = ByteBuffer.allocate(25);
		b.put((byte) 3).putShort((short) 2).put((byte) 2).put("ab".getBytes(StandardCharsets.US_ASCII));
		b.put((byte) 1).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(21.5f).order(java.nio.ByteOrder.BIG_ENDIAN)
				.put((byte) 1).putShort((short) 300);
		b.put((byte) 2).put((byte) 2).put("hi".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
		b.put((byte) 0xAA).put((byte) 0xBB).put((byte) 0xCC).put((byte) 7).put((byte) 8).put((byte) 9);
		return b.array();
	}

	@Test
	void writesWhatReadsBackAndDecodesTheSameData() {
		String east = EastWriter.write(packet());
		assertTrue(east.contains("VIRTUAL_KEY : A_KEY := 0"), east);
		assertTrue(east.contains("for KIND_CODE use (TEMPERATURE => 1, LABEL => 2);"), east);
		assertTrue(east.contains("-- Temperature [degC]: Sensor temperature"), east);
		assertTrue(east.contains("END_OF_DATA : constant EOF;"), east);
		assertTrue(east.contains("SIGN_BIT_NUMBER => 24"), "the little-endian real: " + east);

		FormatDescription back = EastReader.read(east);
		assertEquals(List.of(), DescriptionValidator.validate(back), east);

		List<String> decoded = EastInterpreterTest.lines(new EastInterpreter(east).decode(packetBytes()));
		// Elements with computed counts, lengths and choices are held in records of their own at the top
		// (a variable has no discriminants), and RECORD and BODY are keywords.
		assertEquals(List.of("packet.version = 3", "packet.n = 2", "packet.name_len = 2", "packet.name.name = ab",
				"packet.record_1.record_1[0].kind = 1 (TEMPERATURE)",
				"packet.record_1.record_1[0].body_1.temp_body.temp = 21.5", "packet.record_1.record_1[0].flags = 1",
				"packet.record_1.record_1[0].extra.extra = 300", "packet.record_1.record_1[1].kind = 2 (LABEL)",
				"packet.record_1.record_1[1].body_1.label_body.label_len = 2",
				"packet.record_1.record_1[1].body_1.label_body.label = hi", "packet.record_1.record_1[1].flags = 0",
				"packet.pad.pad[0] = 170", "packet.pad.pad[1] = 187", "packet.pad.pad[2] = 204",
				"packet.trailer[0] = 7", "packet.trailer[1] = 8", "packet.trailer[2] = 9"), decoded, east);
	}

	@Test
	void writesBackWhatItRead() {
		String original = """
				package READINGS is
				   type LEVEL is range -100 .. 100;
				   for LEVEL'size use 16;
				   type STATE is (OFF, ON);
				   for STATE use (OFF => 0, ON => 5);
				   for STATE'size use 8;
				   type R is record
				      H : LEVEL;
				      S : STATE;
				   end record;
				   X : R;
				end READINGS;
				package P is
				   type BIT_ORDER is (HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
				   OCTET_STORAGE : constant BIT_ORDER := LOW_ORDER_FIRST;
				end P;
				""";
		FormatDescription read = EastReader.read(original);
		String written = EastWriter.write(read);
		assertTrue(written.contains("OCTET_STORAGE : constant BIT_ORDER := LOW_ORDER_FIRST;"), written);
		assertTrue(written.contains("is range -100 .. 100;"), written);
		byte[] data = {(byte) 0xF6, (byte) 0xFF, 5};
		assertEquals(EastInterpreterTest.lines(new EastInterpreter(original).decode(data)),
				EastInterpreterTest.lines(new EastInterpreter(written).decode(data)), written);
		assertEquals(EastReaderTest.outline(read.root()), EastReaderTest.outline(EastReader.read(written).root()),
				written);
	}

	@Test
	void refusesWhatEastCantDescribe() {
		RecordDescription text = new RecordDescription("row", "row", List.of(field("a", PrimitiveType.UINT8, null)),
				RecordDescription.TextLayout.CSV, new Occurrence.UntilEnd(), Semantics.NONE);
		FormatDescription csv = new FormatDescription("csv", "", ByteOrder.BIG_ENDIAN, List.of(),
				RecordDescription.of("csv", List.of(text)));
		EastException e = assertThrows(EastException.class, () -> EastWriter.write(csv));
		assertTrue(e.getMessage().contains("'row' is delimited text"), e.getMessage());

		FormatDescription early = new FormatDescription("early", "", ByteOrder.BIG_ENDIAN, List.of(),
				RecordDescription.of("early", List.of(
						field("a", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.UntilEnd()),
						field("b", PrimitiveType.UINT8, null))));
		e = assertThrows(EastException.class, () -> EastWriter.write(early));
		assertTrue(e.getMessage().contains("isn't the last element"), e.getMessage());
	}
}
