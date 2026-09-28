package info.oais.infomodel.structure.description;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Language-specific features: detection, the language check, and their own validity rules. */
class FeatureTest {

	private static FormatDescription format(ElementDescription... elements) {
		return new FormatDescription("f", "", ByteOrder.BIG_ENDIAN, List.of(), RecordDescription.of("f", List.of(elements)));
	}

	private static FieldDescription field(String name, PrimitiveType type, Expression length) {
		return new FieldDescription(name, name, type, length, null, Occurrence.ONCE, Semantics.NONE);
	}

	private static Set<String> messages(List<DescriptionValidator.Problem> problems) {
		return Set.copyOf(problems.stream().map(DescriptionValidator.Problem::message).toList());
	}

	@Test
	void findsWhichFeaturesADescriptionUsesAndWhoCanExpressThem() {
		RecordDescription csv = RecordDescription.of("row", List.of(field("n", PrimitiveType.STRING, null).withNilValue("NA")))
				.withText(RecordDescription.TextLayout.CSV.withQuote("\""));
		FormatDescription f = format(field("flag", PrimitiveType.BITS, new Expression.IntLiteral(1)), csv);

		assertEquals(EnumSet.of(Feature.BIT_FIELDS, Feature.NIL_VALUES, Feature.QUOTED_TEXT), Feature.used(f).keySet());
		assertTrue(Feature.unsupported(f, DescriptionLanguage.DFDL).isEmpty());
		assertEquals(EnumSet.of(Feature.NIL_VALUES, Feature.QUOTED_TEXT), Feature.unsupported(f, DescriptionLanguage.KAITAI));
		assertEquals("Kaitai Struct and DFDL", Feature.BIT_FIELDS.languagesText());
		assertTrue(Feature.BIT_FIELDS.supportedByAll(EnumSet.of(DescriptionLanguage.DFDL, DescriptionLanguage.KAITAI)));
		assertFalse(Feature.BIT_FIELDS.supportedByAll(EnumSet.allOf(DescriptionLanguage.class)));
		Feature.UnsupportedFeatureException e = assertThrows(Feature.UnsupportedFeatureException.class,
				() -> Feature.requireSupported(f, DescriptionLanguage.DRB));
		assertTrue(e.getMessage().contains("bit fields (Kaitai Struct and DFDL only)"), e.getMessage());
	}

	@Test
	void reportsEachElementUsingAFeatureATargetCantExpress() {
		FormatDescription f = format(field("flag", PrimitiveType.BITS, new Expression.IntLiteral(1)),
				field("pad", PrimitiveType.BITS, new Expression.IntLiteral(7)));

		assertTrue(DescriptionValidator.validate(f, EnumSet.of(DescriptionLanguage.DFDL, DescriptionLanguage.KAITAI)).isEmpty());
		assertEquals(Set.of(
				"'flag' uses bit fields, which DRB (Java) can't express (Kaitai Struct and DFDL only). "
						+ "Remove it, or generate only for Kaitai Struct and DFDL.",
				"'pad' uses bit fields, which DRB (Java) can't express (Kaitai Struct and DFDL only). "
						+ "Remove it, or generate only for Kaitai Struct and DFDL."),
				messages(DescriptionValidator.validate(f, EnumSet.of(DescriptionLanguage.DFDL, DescriptionLanguage.DRB))));
	}

	@Test
	void checksTheNewOptionsThemselves() {
		RecordDescription compressedWithoutSize = RecordDescription.of("data", List.of(field("x", PrimitiveType.UINT8, null)))
				.withCompression(RecordDescription.Compression.ZLIB);
		RecordDescription repeatedAtOffset = RecordDescription.of("index", List.of(field("y", PrimitiveType.UINT8, null)))
				.withOffset(Expression.parse("16")).withOccurrence(new Occurrence.Repeated(Expression.parse("2")));
		RecordDescription text = RecordDescription.of("row", List.of(
						field("amount", PrimitiveType.STRING, null).withNumberFormat(new NumberFormat("#,##0", ".", ".")),
						field("flag", PrimitiveType.BITS, new Expression.IntLiteral(1))))
				.withText(RecordDescription.TextLayout.CSV.withQuote(","));
		FormatDescription f = format(field("wide", PrimitiveType.BITS, new Expression.IntLiteral(65)),
				field("label", PrimitiveType.STRING, new Expression.IntLiteral(2)).withNilValue("NA"),
				compressedWithoutSize, repeatedAtOffset, text);

		assertEquals(Set.of(
				"'wide' needs a number of bits from 1 to 64.",
				"Only fields in a delimited-text record can have a nil value; for 'label', describe a value meaning "
						+ "'no data' as its fill value instead.",
				"'data' is compressed, so it needs its (compressed) size in bytes.",
				"'index' is read at an offset, so it occurs once.",
				"The quote in 'row' can't also be a separator.",
				"'amount' isn't a number, so it can't have a number format.",
				"The decimal and grouping separators of 'amount' must differ.",
				"'flag' can't be a bit field in a delimited-text record.",
				"'flag' is in a delimited-text record, so its delimiters end it; remove its length."),
				messages(DescriptionValidator.validate(f)));
	}

	@Test
	void rejectsJavaReservedWordsOnlyWhenGeneratingKaitai() {
		FormatDescription f = format(field("class", PrimitiveType.UINT8, null), field("_io", PrimitiveType.UINT8, null));

		assertTrue(DescriptionValidator.validate(f, EnumSet.of(DescriptionLanguage.DFDL)).isEmpty());
		assertEquals(Set.of(
				"'class' is a reserved word in Java, which Kaitai Struct descriptions are compiled to; choose another "
						+ "name (e.g. 'class_value').",
				"Kaitai Struct keeps names starting with '_' for itself (e.g. _io); choose a name that doesn't start "
						+ "with '_'."),
				messages(DescriptionValidator.validate(f, EnumSet.of(DescriptionLanguage.KAITAI))));
	}
}
