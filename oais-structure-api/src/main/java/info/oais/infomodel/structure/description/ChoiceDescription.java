package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * One of several alternative records, picked by comparing
 * {@code discriminator} (usually an earlier field, e.g. {@code kind}) with
 * each branch's {@code key}. In decoded data a choice appears as a node
 * named {@link #name()} holding one child: the chosen branch's record.
 */
public record ChoiceDescription(String id, String name, Expression discriminator, List<Branch> branches,
		Occurrence occurrence, Semantics semantics) implements ElementDescription {

	/**
	 * One alternative.
	 *
	 * @param key the discriminator value selecting this branch, as text: an
	 *            integer (e.g. {@code "1"}) or a string
	 */
	public record Branch(String key, RecordDescription record) implements Serializable {
		public Branch {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(record, "record");
		}

		/** Whether {@link #key()} is an integer rather than a string. */
		public boolean isIntegerKey() {
			return key.strip().matches("-?\\d+");
		}
	}

	public ChoiceDescription {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(discriminator, "discriminator");
		branches = branches == null ? List.of() : List.copyOf(branches);
		occurrence = occurrence == null ? Occurrence.ONCE : occurrence;
		semantics = semantics == null ? Semantics.NONE : semantics;
	}

	public static ChoiceDescription of(String name, Expression discriminator, List<Branch> branches) {
		return new ChoiceDescription(ElementDescription.newId(), name, discriminator, branches, Occurrence.ONCE,
				Semantics.NONE);
	}

	public ChoiceDescription withBranches(List<Branch> branches) {
		return new ChoiceDescription(id, name, discriminator, branches, occurrence, semantics);
	}

	public ChoiceDescription withOccurrence(Occurrence occurrence) {
		return new ChoiceDescription(id, name, discriminator, branches, occurrence, semantics);
	}

	public ChoiceDescription withSemantics(Semantics semantics) {
		return new ChoiceDescription(id, name, discriminator, branches, occurrence, semantics);
	}
}
