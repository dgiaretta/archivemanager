package info.oais.infomodel.structure.description;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DescriptionModelTest {

	@Test
	void parsesArithmeticComparisonsAndLogicWithTheUsualPrecedence() {
		assertEquals("((n * 2) - 1)", Expression.parse("n * 2 - 1").text());
		assertEquals("(n * (2 - 1))", Expression.parse("n*(2-1)").text());
		assertEquals("((kind = 1) and (flags != 0))", Expression.parse("kind == 1 and flags != 0").text());
		assertEquals("((a = 1) or ((b = 2) and not (c < 3)))", Expression.parse("a=1 or b=2 and not c<3").text());
		assertEquals("(-5 + x)", Expression.parse("-5 + x").text());
		assertEquals("(type = 'A''s')", Expression.parse("type = \"A's\"").text());
		assertEquals(255, ((Expression.IntLiteral) Expression.parse("0xFF")).value());
	}

	@Test
	void textRoundTripsThroughTheParser() {
		for (String text : List.of("n * 2 - 1", "kind = 1 and flags != 0", "not (a >= b % 3)", "name = 'x'")) {
			Expression e = Expression.parse(text);
			assertEquals(e, Expression.parse(e.text()), text);
		}
	}

	@Test
	void reportsWhereAnExpressionIsWrong() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Expression.parse("n * (2"));
		assertTrue(e.getMessage().contains("Missing ')'"), e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> Expression.parse(""));
		assertThrows(IllegalArgumentException.class, () -> Expression.parse("n +"));
		assertThrows(IllegalArgumentException.class, () -> Expression.parse("n ; drop"));
	}

	@Test
	void knowsBooleansFromNumbers() {
		assertTrue(Expression.parse("flags = 1").isBoolean());
		assertTrue(Expression.parse("not x").isBoolean());
		assertFalse(Expression.parse("n * 2").isBoolean());
		assertEquals(List.of(new Expression.FieldRef("rows"), new Expression.FieldRef("cols")),
				Expression.parse("rows * cols + 1").fieldRefs());
	}

	@Test
	void resolvesReferencesWithTheLevelsEachEngineNeeds() {
		FormatDescription packet = PacketFormat.description();
		Scope scope = new Scope(packet);
		// A sibling: DFDL ../tns:flags, DRB ../flags, Kaitai flags.
		assertLevels(scope.resolve("extra", "flags").orElseThrow(), 1, 0);
		// The repeated record's count, from the root record.
		assertLevels(scope.resolve("record", "n").orElseThrow(), 1, 0);
		// The choice's discriminator: DFDL puts it on the choice (../tns:kind).
		assertLevels(scope.resolve("body", "kind").orElseThrow(), 1, 0);
		// From a branch record: DRB's sdf:signature (../../kind).
		String labelBranchId = ((ChoiceDescription) Descriptions.find(packet.root(), "body")
				.orElseThrow()).branches().get(1).record().id();
		assertLevels(scope.resolve(labelBranchId, "kind").orElseThrow(), 2, 0);
		// From a field inside a branch: DFDL ../../../tns:kind, Kaitai _parent.kind.
		assertLevels(scope.resolve("temp", "kind").orElseThrow(), 3, 1);
		// From inside the repeated record out to the root: DFDL ../../tns:n, Kaitai _parent.n.
		assertLevels(scope.resolve("extra", "n").orElseThrow(), 2, 1);
	}

	@Test
	void refusesReferencesToLaterOrRepeatedOrMissingFields() {
		Scope scope = new Scope(PacketFormat.description());
		assertTrue(scope.resolve("name_len", "name").isEmpty(), "a later field");
		assertTrue(scope.resolve("pad", "extra").isEmpty(), "inside a repeated record");
		assertTrue(scope.resolve("pad", "nothing").isEmpty(), "no such field");
	}

	@Test
	void theReferencePacketFormatIsValid() {
		assertEquals(List.of(), DescriptionValidator.validate(PacketFormat.description()));
	}

	@Test
	void explainsWhatIsWrongWithABadDescription() {
		RecordDescription root = RecordDescription.of("bad", List.of(
				FieldDescription.of("label", PrimitiveType.STRING, null, null),
				FieldDescription.of("x", PrimitiveType.INT32, Expression.parse("4"), null),
				FieldDescription.of("rest", PrimitiveType.UINT8, null, null).withOccurrence(new Occurrence.UntilEnd()),
				FieldDescription.of("n", PrimitiveType.FLOAT32, null, null),
				FieldDescription.of("items", PrimitiveType.UINT8, null, null)
						.withOccurrence(new Occurrence.Repeated(Expression.parse("n + later"))),
				FieldDescription.of("Bad Name", PrimitiveType.UINT8, null, null)));
		List<String> messages = DescriptionValidator.validate(
				new FormatDescription("bad", null, null, null, root)).stream().map(DescriptionValidator.Problem::message).toList();

		assertTrue(messages.contains("'label' needs a length in bytes."), messages.toString());
		assertTrue(messages.contains("'x' is a 4-byte number, so it can't have a length."), messages.toString());
		assertTrue(messages.stream().anyMatch(m -> m.startsWith("'rest' repeats until the end")), messages.toString());
		assertTrue(messages.contains("The repeat count of 'items' uses 'n', which isn't a whole number."), messages.toString());
		assertTrue(messages.stream().anyMatch(m -> m.contains("uses 'later', which isn't a field read earlier")), messages.toString());
		assertTrue(messages.stream().anyMatch(m -> m.startsWith("'Bad Name' isn't a valid name")), messages.toString());
	}

	@Test
	void delimitedTextRecordsHoldOnlyPlainFields() {
		RecordDescription row = RecordDescription.of("row", List.of(
				FieldDescription.of("x", PrimitiveType.INT32, null, null),
				FieldDescription.of("blob", PrimitiveType.BYTES, null, null)))
				.withText(RecordDescription.TextLayout.CSV).withOccurrence(new Occurrence.UntilEnd());
		List<String> messages = DescriptionValidator.validate(new FormatDescription("csv", null, null, null,
				RecordDescription.of("points", List.of(row)))).stream().map(DescriptionValidator.Problem::message).toList();

		assertEquals(List.of("'blob' can't be raw bytes in a delimited-text record."), messages);
	}

	@Test
	void editsTheTreeByElementId() {
		RecordDescription root = PacketFormat.description().root();
		RecordDescription added = Descriptions.addChild(root, "record",
				FieldDescription.of("crc", PrimitiveType.UINT16, null, null));
		assertEquals("crc", ((RecordDescription) Descriptions.find(added, "record").orElseThrow()).children().get(4).name());

		RecordDescription moved = Descriptions.move(added, "flags", -1);
		assertEquals(List.of("kind", "flags", "body", "extra", "crc"),
				((RecordDescription) Descriptions.find(moved, "record").orElseThrow()).children().stream()
						.map(ElementDescription::name).toList());

		RecordDescription removed = Descriptions.remove(moved, "crc");
		assertTrue(Descriptions.find(removed, "crc").isEmpty());
		assertTrue(Descriptions.find(removed, "temp").isPresent(), "elements inside choice branches are kept");
	}

	@Test
	void semanticsDecodeCodedScaledAndFillValues() {
		Semantics s = new Semantics("Pressure", null, "hPa", null, null, Map.of("65535", "sensor off"),
				new BigDecimal("0.1"), new BigDecimal("800"), "65535", null, null);
		assertEquals("sensor off", s.meaningOf(65535L));
		assertEquals("sensor off", s.meaningOf((short) -1 == -1 ? 65535 : 0));
		assertEquals(new BigDecimal("812.3"), s.physicalValue(123));
		assertTrue(s.isFill(65535));
		assertFalse(s.isFill(12));
		assertTrue(Semantics.of(" ", null, null).isEmpty());
	}

	private static void assertLevels(Scope.Resolved r, int nodesUp, int typesUp) {
		assertEquals(nodesUp, r.nodesUp(), "nodesUp to " + r.target().name());
		assertEquals(typesUp, r.typesUp(), "typesUp to " + r.target().name());
	}
}
