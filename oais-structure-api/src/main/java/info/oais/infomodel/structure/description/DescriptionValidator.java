package info.oais.infomodel.structure.description;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a {@link FormatDescription} for everything that would make it
 * ambiguous or impossible to generate for every engine. Returns problems
 * rather than throwing, so an editor can show them next to the elements
 * they're about.
 */
public final class DescriptionValidator {

	/** One problem, with the id of the element it's about (null for the format as a whole). */
	public record Problem(String elementId, String message) {
	}

	private static final String NAME_PATTERN = "[a-z_][a-z0-9_]*";

	private DescriptionValidator() {
	}

	public static List<Problem> validate(FormatDescription format) {
		List<Problem> problems = new ArrayList<>();
		Scope scope = new Scope(format);
		if (!(format.root().occurrence() instanceof Occurrence.Once)) {
			problems.add(new Problem(format.root().id(), "The whole file (the root record) must occur once."));
		}
		checkRecord(format.root(), scope, problems);
		return problems;
	}

	private static void checkRecord(RecordDescription record, Scope scope, List<Problem> problems) {
		checkName(record, problems);
		checkOccurrence(record, scope, problems);
		if (record.children().isEmpty()) {
			problems.add(new Problem(record.id(), "'" + record.name() + "' has no elements yet."));
		}
		Set<String> names = new HashSet<>();
		List<ElementDescription> children = record.children();
		for (int i = 0; i < children.size(); i++) {
			ElementDescription child = children.get(i);
			if (!names.add(child.name())) {
				problems.add(new Problem(child.id(), "There is already an element called '" + child.name()
						+ "' in '" + record.name() + "'."));
			}
			if (child.occurrence() instanceof Occurrence.UntilEnd && i < children.size() - 1) {
				problems.add(new Problem(child.id(), "'" + child.name()
						+ "' repeats until the end of the data, so it must be the last element of '"
						+ record.name() + "'."));
			}
			if (record.isText()) {
				checkTextChild(child, problems);
			}
			if (child instanceof FieldDescription f) {
				checkField(f, record.isText(), scope, problems);
			} else if (child instanceof RecordDescription r) {
				checkRecord(r, scope, problems);
			} else if (child instanceof ChoiceDescription c) {
				checkChoice(c, scope, problems);
			}
		}
	}

	private static void checkTextChild(ElementDescription child, List<Problem> problems) {
		if (!(child instanceof FieldDescription f)) {
			problems.add(new Problem(child.id(), "A delimited-text record can only contain fields."));
			return;
		}
		if (f.type() == PrimitiveType.BYTES) {
			problems.add(new Problem(f.id(), "'" + f.name() + "' can't be raw bytes in a delimited-text record."));
		}
		if (!(f.occurrence() instanceof Occurrence.Once)) {
			problems.add(new Problem(f.id(), "Fields in a delimited-text record occur once each; repeat the record instead."));
		}
	}

	private static void checkField(FieldDescription f, boolean inText, Scope scope, List<Problem> problems) {
		checkName(f, problems);
		checkOccurrence(f, scope, problems);
		if (inText) {
			if (f.length() != null) {
				problems.add(new Problem(f.id(), "'" + f.name()
						+ "' is in a delimited-text record, so its delimiters end it; remove its length."));
			}
			return;
		}
		if (f.type().needsLength() && f.length() == null) {
			problems.add(new Problem(f.id(), "'" + f.name() + "' needs a length in bytes."));
		} else if (!f.type().needsLength() && f.length() != null) {
			problems.add(new Problem(f.id(), "'" + f.name() + "' is a " + f.type().fixedWidth()
					+ "-byte number, so it can't have a length."));
		}
		if (f.length() != null) {
			checkNumeric(f, f.length(), "length", scope, problems);
		}
	}

	private static void checkChoice(ChoiceDescription c, Scope scope, List<Problem> problems) {
		checkName(c, problems);
		checkOccurrence(c, scope, problems);
		if (c.branches().isEmpty()) {
			problems.add(new Problem(c.id(), "Choice '" + c.name() + "' has no branches yet."));
		}
		if (c.discriminator().isBoolean()) {
			problems.add(new Problem(c.id(), "The choice is made on a value, e.g. 'kind', not a true/false test."));
		}
		boolean integerDiscriminator = checkRefs(c, c.discriminator(), "choice", scope, problems);
		Set<String> keys = new HashSet<>();
		Set<String> branchNames = new HashSet<>();
		for (ChoiceDescription.Branch b : c.branches()) {
			if (!keys.add(b.key().strip())) {
				problems.add(new Problem(b.record().id(), "Two branches of '" + c.name() + "' have the value '"
						+ b.key() + "'."));
			}
			if (!branchNames.add(b.record().name())) {
				problems.add(new Problem(b.record().id(), "Two branches of '" + c.name() + "' are called '"
						+ b.record().name() + "'."));
			}
			if (integerDiscriminator && !b.isIntegerKey()) {
				problems.add(new Problem(b.record().id(), "'" + c.name()
						+ "' is chosen by a number, so branch values must be whole numbers (not '" + b.key() + "')."));
			}
			if (!(b.record().occurrence() instanceof Occurrence.Once)) {
				problems.add(new Problem(b.record().id(), "A branch occurs once; repeat the choice instead."));
			}
			checkRecord(b.record(), scope, problems);
		}
	}

	private static void checkOccurrence(ElementDescription e, Scope scope, List<Problem> problems) {
		if (e.occurrence() instanceof Occurrence.Repeated r) {
			checkNumeric(e, r.count(), "repeat count", scope, problems);
		} else if (e.occurrence() instanceof Occurrence.Optional o) {
			if (!o.condition().isBoolean()) {
				problems.add(new Problem(e.id(), "The condition for '" + e.name()
						+ "' must be a test, e.g. 'flags = 1'."));
			}
			checkRefs(e, o.condition(), "condition", scope, problems);
		}
	}

	private static void checkNumeric(ElementDescription e, Expression expr, String what, Scope scope,
			List<Problem> problems) {
		if (expr.isBoolean()) {
			problems.add(new Problem(e.id(), "The " + what + " of '" + e.name() + "' must be a number, not a test."));
		}
		if (expr instanceof Expression.StringLiteral) {
			problems.add(new Problem(e.id(), "The " + what + " of '" + e.name() + "' must be a number."));
		}
		for (Expression.FieldRef ref : expr.fieldRefs()) {
			scope.resolve(e.id(), ref.name()).ifPresent(r -> {
				if (!r.target().type().isInteger()) {
					problems.add(new Problem(e.id(), "The " + what + " of '" + e.name() + "' uses '" + ref.name()
							+ "', which isn't a whole number."));
				}
			});
		}
		checkRefs(e, expr, what, scope, problems);
	}

	/** @return whether every reference resolved to an integer field (true when there are none) */
	private static boolean checkRefs(ElementDescription e, Expression expr, String what, Scope scope,
			List<Problem> problems) {
		boolean allInteger = true;
		for (Expression.FieldRef ref : expr.fieldRefs()) {
			var resolved = scope.resolve(e.id(), ref.name());
			if (resolved.isEmpty()) {
				problems.add(new Problem(e.id(), "The " + what + " of '" + e.name() + "' uses '" + ref.name()
						+ "', which isn't a field read earlier that always occurs once."));
				allInteger = false;
			} else if (!resolved.get().target().type().isInteger()) {
				allInteger = false;
			}
		}
		return allInteger && !(expr instanceof Expression.StringLiteral);
	}

	private static void checkName(ElementDescription e, List<Problem> problems) {
		if (!e.name().matches(NAME_PATTERN)) {
			problems.add(new Problem(e.id(), "'" + e.name()
					+ "' isn't a valid name: use lower-case letters, digits and underscores, not starting with a digit."));
		}
	}
}
