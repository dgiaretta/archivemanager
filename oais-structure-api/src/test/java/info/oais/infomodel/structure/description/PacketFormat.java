package info.oais.infomodel.structure.description;

import java.util.List;
import java.util.Map;

/**
 * The reference "packet" format used to prove each construct on the real
 * engines: counts and lengths from fields, arithmetic, a choice, an
 * optional field, a little-endian float and repeat-until-end.
 */
final class PacketFormat {

	private PacketFormat() {
	}

	static FormatDescription description() {
		RecordDescription temp = RecordDescription.of("temp_body", List.of(
				new FieldDescription("temp", "temp", PrimitiveType.FLOAT32, null, ByteOrder.LITTLE_ENDIAN,
						Occurrence.ONCE, Semantics.of("Temperature", "Sensor temperature", "degC"))));
		RecordDescription label = RecordDescription.of("label_body", List.of(
				field("label_len", PrimitiveType.UINT8, null),
				field("label", PrimitiveType.STRING, Expression.parse("label_len"))));
		ChoiceDescription body = new ChoiceDescription("body", "body", Expression.parse("kind"),
				List.of(new ChoiceDescription.Branch("1", temp), new ChoiceDescription.Branch("2", label)),
				Occurrence.ONCE, Semantics.NONE);
		RecordDescription record = new RecordDescription("record", "record", List.of(
				new FieldDescription("kind", "kind", PrimitiveType.UINT8, null, null, Occurrence.ONCE,
						new Semantics("Record kind", null, null, null, null,
								Map.of("1", "temperature", "2", "label"), null, null, null, null, null)),
				body,
				field("flags", PrimitiveType.UINT8, null),
				field("extra", PrimitiveType.UINT16, null)
						.withOccurrence(new Occurrence.Optional(Expression.parse("flags = 1")))),
				null, new Occurrence.Repeated(Expression.parse("n")), Semantics.NONE);
		RecordDescription root = new RecordDescription("packet", "packet", List.of(
				field("version", PrimitiveType.UINT8, null),
				field("n", PrimitiveType.UINT16, null),
				field("name_len", PrimitiveType.UINT8, null),
				field("name", PrimitiveType.STRING, Expression.parse("name_len")),
				record,
				field("pad", PrimitiveType.BYTES, Expression.parse("n * 2 - 1")),
				field("trailer", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.UntilEnd())),
				null, Occurrence.ONCE, Semantics.NONE);
		return new FormatDescription("packet", "", ByteOrder.BIG_ENDIAN, List.of("pkt"), root);
	}

	private static FieldDescription field(String name, PrimitiveType type, Expression length) {
		return new FieldDescription(name, name, type, length, null, Occurrence.ONCE, Semantics.NONE);
	}
}
