package info.oais.infomodel.structure.description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a {@link FormatDescription} for everything that would make it
 * ambiguous or impossible to generate. Returns problems rather than
 * throwing, so an editor can show them next to the elements they're about.
 * {@link #validate(FormatDescription, Collection)} also checks that the
 * languages it's meant for can express every {@link Feature} it uses.
 */
public final class DescriptionValidator {

	/** One problem, with the id of the element it's about (null for the format as a whole). */
	public record Problem(String elementId, String message) {
	}

	private static final String NAME_PATTERN = "[a-z_][a-z0-9_]*";

	/**
	 * Java's reserved words: the Kaitai Struct compiler names Java fields and
	 * methods after elements without renaming these, so the Java wouldn't compile.
	 */
	private static final Set<String> JAVA_RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
			"catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
			"final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
			"interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
			"static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
			"void", "volatile", "while", "true", "false", "null", "var", "yield", "record");

	private DescriptionValidator() {
	}

	public static List<Problem> validate(FormatDescription format) {
		List<Problem> problems = new ArrayList<>();
		Scope scope = new Scope(format);
		if (!(format.root().occurrence() instanceof Occurrence.Once)) {
			problems.add(new Problem(format.root().id(), "The whole file (the root record) must occur once."));
		}
		if (format.root().offset() != null || format.root().size() != null || format.root().compression() != null) {
			problems.add(new Problem(format.root().id(),
					"The whole file (the root record) can't have an offset, a stated size or compression."));
		}
		checkRecord(format.root(), scope, problems);
		return problems;
	}

	/**
	 * {@link #validate(FormatDescription)}, plus a problem for each element
	 * using a feature one of {@code targets} can't express.
	 */
	public static List<Problem> validate(FormatDescription format, Collection<DescriptionLanguage> targets) {
		List<Problem> problems = validate(format);
		if (targets.contains(DescriptionLanguage.KAITAI)) {
			for (ElementDescription e : Descriptions.all(format.root())) {
				if (e == format.root()) {
					continue;
				}
				if (JAVA_RESERVED.contains(e.name())) {
					problems.add(new Problem(e.id(), "'" + e.name() + "' is a reserved word in Java, which Kaitai Struct"
							+ " descriptions are compiled to; choose another name (e.g. '" + e.name() + "_value')."));
				} else if (e.name().startsWith("_")) {
					problems.add(new Problem(e.id(), "Kaitai Struct keeps names starting with '_' for itself (e.g. _io);"
							+ " choose a name that doesn't start with '_'."));
				}
			}
		}
		for (Map.Entry<Feature, List<ElementDescription>> used : Feature.used(format).entrySet()) {
			Feature feature = used.getKey();
			List<String> missing = targets.stream().filter(l -> !feature.supportedBy(l))
					.map(DescriptionLanguage::label).toList();
			if (missing.isEmpty()) {
				continue;
			}
			for (ElementDescription e : used.getValue()) {
				problems.add(new Problem(e.id(), "'" + e.name() + "' uses " + feature.label() + ", which "
						+ String.join(" and ", missing) + " can't express (" + feature.languagesText()
						+ " only). Remove it, or generate only for " + feature.languagesText() + "."));
			}
		}
		return problems;
	}

	private static void checkRecord(RecordDescription record, Scope scope, List<Problem> problems) {
		checkName(record, problems);
		checkOccurrence(record, scope, problems);
		checkOffset(record, scope, problems);
		if (record.size() != null) {
			checkNumeric(record, record.size(), "size", scope, problems);
			if (record.isText()) {
				problems.add(new Problem(record.id(), "A delimited-text record is ended by its terminator, so '"
						+ record.name() + "' can't also have a stated size."));
			}
		}
		if (record.compression() != null) {
			if (record.size() == null) {
				problems.add(new Problem(record.id(), "'" + record.name()
						+ "' is compressed, so it needs its (compressed) size in bytes."));
			}
			if (record.isText()) {
				problems.add(new Problem(record.id(), "A delimited-text record can't be compressed."));
			}
		}
		if (record.isText() && record.text().quote() != null) {
			String q = record.text().quote();
			if (q.length() != 1) {
				problems.add(new Problem(record.id(), "The quote in '" + record.name() + "' must be one character."));
			} else if (record.text().fieldSeparator().contains(q) || record.text().recordTerminator().contains(q)) {
				problems.add(new Problem(record.id(), "The quote in '" + record.name()
						+ "' can't also be a separator."));
			}
		}
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
		if (f.type() == PrimitiveType.BITS) {
			problems.add(new Problem(f.id(), "'" + f.name() + "' can't be a bit field in a delimited-text record."));
		}
		if (!(f.occurrence() instanceof Occurrence.Once)) {
			problems.add(new Problem(f.id(), "Fields in a delimited-text record occur once each; repeat the record instead."));
		}
		if (f.offset() != null) {
			problems.add(new Problem(f.id(), "Fields in a delimited-text record are read in order, so '" + f.name()
					+ "' can't have an offset."));
		}
	}

	private static void checkField(FieldDescription f, boolean inText, Scope scope, List<Problem> problems) {
		checkName(f, problems);
		checkOccurrence(f, scope, problems);
		checkOffset(f, scope, problems);
		if (inText) {
			if (f.length() != null) {
				problems.add(new Problem(f.id(), "'" + f.name()
						+ "' is in a delimited-text record, so its delimiters end it; remove its length."));
			}
			if (f.numberFormat() != null) {
				if (!f.type().isNumeric()) {
					problems.add(new Problem(f.id(), "'" + f.name() + "' isn't a number, so it can't have a number format."));
				}
				NumberFormat nf = f.numberFormat();
				if (nf.pattern().isBlank()) {
					problems.add(new Problem(f.id(), "The number format of '" + f.name() + "' needs a pattern, e.g. #,##0.00."));
				}
				if (nf.decimalSeparator().length() != 1
						|| (nf.groupingSeparator() != null && nf.groupingSeparator().length() != 1)) {
					problems.add(new Problem(f.id(), "The decimal and grouping separators of '" + f.name()
							+ "' must be one character each."));
				} else if (nf.decimalSeparator().equals(nf.groupingSeparator())) {
					problems.add(new Problem(f.id(), "The decimal and grouping separators of '" + f.name()
							+ "' must differ."));
				}
			}
			return;
		}
		if (f.nilValue() != null) {
			problems.add(new Problem(f.id(), "Only fields in a delimited-text record can have a nil value; for '"
					+ f.name() + "', describe a value meaning 'no data' as its fill value instead."));
		}
		if (f.numberFormat() != null) {
			problems.add(new Problem(f.id(), "Only numbers in a delimited-text record can have a number format."));
		}
		if (f.type() == PrimitiveType.BITS) {
			if (!(f.length() instanceof Expression.IntLiteral lit) || lit.value() < 1 || lit.value() > 64) {
				problems.add(new Problem(f.id(), "'" + f.name() + "' needs a number of bits from 1 to 64."));
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

	private static void checkOffset(ElementDescription e, Scope scope, List<Problem> problems) {
		Expression offset = e instanceof FieldDescription f ? f.offset()
				: e instanceof RecordDescription r ? r.offset() : null;
		if (offset == null) {
			return;
		}
		checkNumeric(e, offset, "offset", scope, problems);
		if (!(e.occurrence() instanceof Occurrence.Once)) {
			problems.add(new Problem(e.id(), "'" + e.name() + "' is read at an offset, so it occurs once."));
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
