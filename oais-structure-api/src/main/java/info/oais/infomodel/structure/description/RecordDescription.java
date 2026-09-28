package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * A group of elements, in data order.
 *
 * @param text for a delimited-text record (e.g. one CSV row): how its fields are
 *             separated and the record ended; null for a binary record, whose
 *             fields follow one another with no delimiters
 */
public record RecordDescription(String id, String name, List<ElementDescription> children, TextLayout text,
		Occurrence occurrence, Semantics semantics) implements ElementDescription {

	/**
	 * Delimited text: fields separated by {@code fieldSeparator} (e.g.
	 * {@code ","}) and the record ended by {@code recordTerminator} (e.g. a
	 * newline). Quoted values are not supported.
	 */
	public record TextLayout(String fieldSeparator, String recordTerminator) implements Serializable {
		public TextLayout {
			Objects.requireNonNull(fieldSeparator, "fieldSeparator");
			Objects.requireNonNull(recordTerminator, "recordTerminator");
			if (fieldSeparator.isEmpty() || recordTerminator.isEmpty()) {
				throw new IllegalArgumentException("Separators can't be empty");
			}
		}

		/** Comma-separated values, one record per line. */
		public static final TextLayout CSV = new TextLayout(",", "\n");
	}

	public RecordDescription {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		children = children == null ? List.of() : List.copyOf(children);
		occurrence = occurrence == null ? Occurrence.ONCE : occurrence;
		semantics = semantics == null ? Semantics.NONE : semantics;
	}

	/** A binary record occurring once, with a new id. */
	public static RecordDescription of(String name, List<ElementDescription> children) {
		return new RecordDescription(ElementDescription.newId(), name, children, null, Occurrence.ONCE, Semantics.NONE);
	}

	public boolean isText() {
		return text != null;
	}

	public RecordDescription withChildren(List<ElementDescription> children) {
		return new RecordDescription(id, name, children, text, occurrence, semantics);
	}

	public RecordDescription withOccurrence(Occurrence occurrence) {
		return new RecordDescription(id, name, children, text, occurrence, semantics);
	}

	public RecordDescription withText(TextLayout text) {
		return new RecordDescription(id, name, children, text, occurrence, semantics);
	}

	public RecordDescription withSemantics(Semantics semantics) {
		return new RecordDescription(id, name, children, text, occurrence, semantics);
	}
}
