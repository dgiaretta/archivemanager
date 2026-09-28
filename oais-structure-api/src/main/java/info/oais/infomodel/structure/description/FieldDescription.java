package info.oais.infomodel.structure.description;

import java.util.Objects;

/**
 * A single value.
 *
 * @param length    for {@link PrimitiveType#STRING}/{@link PrimitiveType#BYTES} in a binary
 *                  record: the length in bytes, a literal or computed from earlier fields;
 *                  null otherwise (numeric types have a fixed width, and delimited text is
 *                  bounded by its delimiters)
 * @param byteOrder for multi-byte numbers: overrides the format's default; null means the default
 */
public record FieldDescription(String id, String name, PrimitiveType type, Expression length, ByteOrder byteOrder,
		Occurrence occurrence, Semantics semantics) implements ElementDescription {

	public FieldDescription {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(type, "type");
		occurrence = occurrence == null ? Occurrence.ONCE : occurrence;
		semantics = semantics == null ? Semantics.NONE : semantics;
	}

	/** A field occurring once, with a new id. */
	public static FieldDescription of(String name, PrimitiveType type, Expression length, Semantics semantics) {
		return new FieldDescription(ElementDescription.newId(), name, type, length, null, Occurrence.ONCE, semantics);
	}

	public FieldDescription withOccurrence(Occurrence occurrence) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics);
	}

	public FieldDescription withByteOrder(ByteOrder byteOrder) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics);
	}

	public FieldDescription withSemantics(Semantics semantics) {
		return new FieldDescription(id, name, type, length, byteOrder, occurrence, semantics);
	}
}
