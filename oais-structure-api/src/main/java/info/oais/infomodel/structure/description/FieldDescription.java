package info.oais.infomodel.structure.description;

import java.util.Objects;

/**
 * A single value.
 *
 * @param length       for {@link PrimitiveType#STRING}/{@link PrimitiveType#BYTES} in a binary
 *                     record: the length in bytes, a literal or computed from earlier fields; for
 *                     {@link PrimitiveType#BITS}: the number of bits; null otherwise (numeric types
 *                     have a fixed width, and delimited text is bounded by its delimiters)
 * @param byteOrder    for multi-byte numbers: overrides the format's default; null means the default
 * @param offset       where the field is, in bytes from the start of the file (or of the enclosing
 *                     record of a stated size), read out of sequence; null to read it in sequence.
 *                     {@link Feature#ABSOLUTE_OFFSETS}
 * @param nilValue     in delimited text: the text that means "no value", e.g. {@code NA}, or the
 *                     empty string for an empty field; null if the field always has a value.
 *                     {@link Feature#NIL_VALUES}
 * @param numberFormat in delimited text, for a number: how it's written, if not plain digits.
 *                     {@link Feature#NUMBER_FORMATS}
 */
public record FieldDescription(String id, String name, PrimitiveType type, Expression length, ByteOrder byteOrder,
		Occurrence occurrence, Semantics semantics, Expression offset, String nilValue, NumberFormat numberFormat)
		implements ElementDescription {

	public FieldDescription {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(type, "type");
		occurrence = occurrence == null ? Occurrence.ONCE : occurrence;
		semantics = semantics == null ? Semantics.NONE : semantics;
	}

	/** A field read in sequence, with none of the language-specific options. */
	public FieldDescription(String id, String name, PrimitiveType type, Expression length, ByteOrder byteOrder,
			Occurrence occurrence, Semantics semantics) {
		this(id, name, type, length, byteOrder, occurrence, semantics, null, null, null);
	}

	/** A field occurring once, with a new id. */
	public static FieldDescription of(String name, PrimitiveType type, Expression length, Semantics semantics) {
		return new FieldDescription(ElementDescription.newId(), name, type, length, null, Occurrence.ONCE, semantics);
	}

	public FieldDescription withOccurrence(Occurrence occurrence) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}

	public FieldDescription withByteOrder(ByteOrder byteOrder) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}

	public FieldDescription withSemantics(Semantics semantics) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}

	public FieldDescription withOffset(Expression offset) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}

	public FieldDescription withNilValue(String nilValue) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}

	public FieldDescription withNumberFormat(NumberFormat numberFormat) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics, offset, nilValue,
				numberFormat);
	}
}
