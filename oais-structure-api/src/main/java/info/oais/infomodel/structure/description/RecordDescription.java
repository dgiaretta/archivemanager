package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * A group of elements, in data order.
 *
 * @param text        for a delimited-text record (e.g. one CSV row): how its fields are
 *                    separated and the record ended; null for a binary record, whose
 *                    fields follow one another with no delimiters
 * @param size        the record's size in bytes, if stated: its elements are read within
 *                    that many bytes, and any bytes they don't use are skipped. Null if the
 *                    record is just as long as its elements. {@link Feature#SIZED_RECORDS}
 * @param compression how the record's {@code size} bytes are compressed; null if they
 *                    aren't. {@link Feature#COMPRESSION}
 * @param offset      where the record is, in bytes from the start of the file (or of the
 *                    enclosing record of a stated size), read out of sequence; null to read
 *                    it in sequence. {@link Feature#ABSOLUTE_OFFSETS}
 */
public record RecordDescription(String id, String name, List<ElementDescription> children, TextLayout text,
		Occurrence occurrence, Semantics semantics, Expression size, Compression compression, Expression offset)
		implements ElementDescription {

	/**
	 * Delimited text: fields separated by {@code fieldSeparator} (e.g.
	 * {@code ","}) and the record ended by {@code recordTerminator} (e.g. a
	 * newline).
	 *
	 * @param quote the character that may enclose a value containing separators, with a
	 *              doubled quote inside meaning one quote (as in RFC 4180 CSV); null if values
	 *              are never quoted. {@link Feature#QUOTED_TEXT}
	 */
	public record TextLayout(String fieldSeparator, String recordTerminator, String quote) implements Serializable {
		public TextLayout {
			Objects.requireNonNull(fieldSeparator, "fieldSeparator");
			Objects.requireNonNull(recordTerminator, "recordTerminator");
			if (fieldSeparator.isEmpty() || recordTerminator.isEmpty()) {
				throw new IllegalArgumentException("Separators can't be empty");
			}
			quote = quote == null || quote.isEmpty() ? null : quote;
		}

		public TextLayout(String fieldSeparator, String recordTerminator) {
			this(fieldSeparator, recordTerminator, null);
		}

		/** Comma-separated values, one record per line. */
		public static final TextLayout CSV = new TextLayout(",", "\n");

		public TextLayout withQuote(String quote) {
			return new TextLayout(fieldSeparator, recordTerminator, quote);
		}
	}

	/** A compression scheme for a record's bytes. */
	public enum Compression {
		/** zlib (RFC 1950), as written by e.g. {@code java.util.zip.Deflater} or Python's {@code zlib}. */
		ZLIB
	}

	public RecordDescription {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		children = children == null ? List.of() : List.copyOf(children);
		occurrence = occurrence == null ? Occurrence.ONCE : occurrence;
		semantics = semantics == null ? Semantics.NONE : semantics;
	}

	/** A record read in sequence, with none of the language-specific options. */
	public RecordDescription(String id, String name, List<ElementDescription> children, TextLayout text,
			Occurrence occurrence, Semantics semantics) {
		this(id, name, children, text, occurrence, semantics, null, null, null);
	}

	/** A binary record occurring once, with a new id. */
	public static RecordDescription of(String name, List<ElementDescription> children) {
		return new RecordDescription(ElementDescription.newId(), name, children, null, Occurrence.ONCE, Semantics.NONE);
	}

	public boolean isText() {
		return text != null;
	}

	public RecordDescription withChildren(List<ElementDescription> children) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withOccurrence(Occurrence occurrence) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withText(TextLayout text) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withSemantics(Semantics semantics) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withSize(Expression size) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withCompression(Compression compression) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}

	public RecordDescription withOffset(Expression offset) {
		return new RecordDescription(id, name, children, text, occurrence, semantics, size, compression, offset);
	}
}
