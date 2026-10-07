package info.oais.infomodel.structure.east;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.Expression.FieldRef;
import info.oais.infomodel.structure.description.Expression.Op;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Scope;
import info.oais.infomodel.structure.description.Semantics;

/**
 * Writes a {@link FormatDescription} as an EAST Data Description Record
 * (CCSDS 644.0-B-3): a logical package of types and the variables the data
 * holds, and a physical package giving the byte order and the
 * representation of every real and every integer whose byte order isn't the
 * description's own.
 *
 * <p>Each record becomes a record type and each repeated element an array.
 * Fields with code lists become enumerations whose literals are their
 * meanings, with an enumeration representation clause for their codes; a
 * valid range becomes an integer type's range. What the element tree
 * computes - counts, lengths, conditions and choices - becomes virtual
 * discriminants whose actual values are given at the end of the logical
 * package, with EAST paths for the fields they use. Since a variant part
 * comes last in an EAST record, an optional element or a choice becomes a
 * record of its own with that name: an optional element holds the element
 * when its condition holds, and a choice holds the branch its key picks.
 * Semantic names, definitions, units, scaling and fill values become
 * comments.</p>
 *
 * <p>An EAST description describes one set of data, applied repeatedly to
 * the whole of the data, so a description whose root is a record
 * {@code set} repeated to the end (as {@link EastReader} reads them) is
 * written as that set; an element repeated to the end of the data otherwise
 * becomes the last variable, followed by an EOF marker.</p>
 *
 * <p>What EAST can't describe is refused with an {@link EastException}
 * naming the element: delimited text, elements at an absolute offset,
 * compressed records, records whose size is computed, choices on text,
 * repetition to the end anywhere but last in the data, and a repeated text
 * or byte string whose length is computed.</p>
 */
public final class EastWriter {

	/** Keywords of EAST and Ada (CCSDS 644.0-B-3 section 4), which can't be names. */
	private static final Set<String> RESERVED = Set.of("ABORT", "ABS", "ABSTRACT", "ACCEPT", "ACCESS", "ALIASED",
			"ALL", "AND", "ARRAY", "AT", "BEGIN", "BODY", "CASE", "CONSTANT", "DECLARE", "DELAY", "DELTA", "DIGITS",
			"DO", "ELSE", "ELSIF", "END", "ENTRY", "EXCEPTION", "EXIT", "FOR", "FUNCTION", "GENERIC", "GOTO", "IF",
			"IN", "IS", "LIMITED", "LOOP", "MOD", "NEW", "NOT", "NULL", "OF", "OR", "OTHERS", "OUT", "PACKAGE",
			"PRAGMA", "PRIVATE", "PROCEDURE", "PROTECTED", "RAISE", "RANGE", "RECORD", "REM", "RENAMES", "REQUEUE",
			"RETURN", "REVERSE", "SELECT", "SEPARATE", "SUBTYPE", "TAGGED", "TASK", "TERMINATE", "THEN", "TYPE",
			"UNTIL", "USE", "WHEN", "WHILE", "WITH", "XOR", "VIRTUAL", "WORD_32_BITS", "WORD_16_BITS", "EAST_VERSION",
			"CHARACTER", "STRING", "EOF", "BOOLEAN", "TRUE", "FALSE", "ASCII");

	/**
	 * @return the EAST description
	 * @throws EastException if the description uses what EAST can't describe
	 */
	public static String write(FormatDescription format) {
		return new EastWriter(format).write();
	}

	private final FormatDescription format;
	private final Scope scope;
	private final ByteOrder order;
	private final StringBuilder types = new StringBuilder();
	private final List<String> actuals = new ArrayList<>();
	/** The EAST path of each element written so far, by id, for the expressions that use them. */
	private final Map<String, String> pathOf = new HashMap<>();
	private final Set<String> names = new HashSet<>();
	/** Scalar types shared between fields, by what they are. */
	private final Map<String, String> shared = new HashMap<>();
	/** Physical representations: the user type each is for, its record type and its value. */
	private final Map<String, String[]> reps = new LinkedHashMap<>();
	private boolean index;
	private boolean key;
	private boolean octets;

	private EastWriter(FormatDescription format) {
		this.format = format;
		this.scope = new Scope(format);
		this.order = format.defaultByteOrder();
	}

	/** The components (or variables) of one record being written, and its virtual discriminants. */
	private final class Rec {
		final String path;
		final List<String> discriminants = new ArrayList<>();
		final List<String> lines = new ArrayList<>();
		private final Set<String> virtuals = new HashSet<>();

		Rec(String path) {
			this.path = path;
		}

		/** Adds a virtual discriminant whose actual value is {@code value}, as seen from the element {@code fromId}. */
		String virtual(String hint, String type, String initial, Expression value, String fromId) {
			String name = "VIRTUAL_" + hint;
			for (int i = 2; !virtuals.add(name); i++) {
				name = "VIRTUAL_" + hint + "_" + i;
			}
			discriminants.add(name + " : " + type + " := " + initial);
			actuals.add(path + "." + name + " : virtual " + type + " := " + render(value, fromId) + ";");
			return name;
		}

		void add(List<String> comments, String declaration) {
			lines.addAll(comments);
			lines.add(declaration);
		}
	}

	private String write() {
		RecordDescription root = format.root();
		List<ElementDescription> children = root.children();
		if (children.size() == 1 && children.get(0) instanceof RecordDescription set && set.name().equals("set")
				&& set.occurrence() instanceof Occurrence.UntilEnd && !set.isText()) {
			children = set.children();
		}
		String pkg = eastName(root.name());
		Rec variables = new Rec(null);
		boolean untilEof = false;
		for (int i = 0; i < children.size(); i++) {
			ElementDescription e = children.get(i);
			if (e.occurrence() instanceof Occurrence.UntilEnd) {
				if (i != children.size() - 1) {
					throw refuse(e, "repeats to the end of the data but isn't the last element");
				}
				untilEof = true;
			}
			String name = eastName(e.name());
			if (needsDiscriminant(e)) {
				// A variable can't have discriminants: hold the element in a record of its own.
				Rec holder = new Rec(name);
				element(e, holder, name);
				String type = record(unique(name + "_HOLDER"), holder, null, semanticLines(e.semantics(), "   "));
				variables.add(List.of(), name + " : " + type + ";");
			} else {
				element(e, variables, null);
			}
		}

		StringBuilder out = new StringBuilder();
		out.append("-- ").append(format.name()).append(": written as an EAST Data Description Record (CCSDS 644.0-B-3)\n");
		out.append("-- by RepInfo Tools.\n");
		for (String line : wrap(format.notes(), 76)) {
			out.append("-- ").append(line).append('\n');
		}
		if (!format.fileExtensions().isEmpty()) {
			out.append("-- File extensions: ").append(String.join(", ", format.fileExtensions())).append('\n');
		}
		out.append("package ").append(pkg).append(" is\n");
		out.append("   east_version : constant STRING := \"3.0\";\n");
		out.append("   -- tool version : RepInfo Tools EastWriter\n");
		if (index) {
			out.append("   type AN_INDEX is range 0 .. 2_147_483_647;\n");
		}
		if (key) {
			out.append("   type A_KEY is range -9_223_372_036_854_775_807 .. 9_223_372_036_854_775_807;\n");
		}
		if (octets) {
			out.append("   type OCTET is range 0 .. 255;\n   for OCTET'size use 8;\n");
			out.append("   type OCTETS is array (AN_INDEX range <>) of OCTET;\n");
		}
		out.append(types);
		for (String line : variables.lines) {
			out.append("   ").append(line).append('\n');
		}
		if (untilEof) {
			out.append("   END_OF_DATA : constant EOF;\n");
		}
		if (!actuals.isEmpty()) {
			out.append("   -- Actual values of the discriminants\n");
			for (String actual : actuals) {
				out.append("   ").append(actual).append('\n');
			}
		}
		out.append("end ").append(pkg).append(";\n\n");
		physical(out, pkg + "_PHYSICAL");
		return out.toString();
	}

	/** Whether an element at the top needs a virtual discriminant, which only a record can have. */
	private static boolean needsDiscriminant(ElementDescription e) {
		if (e instanceof ChoiceDescription || e.occurrence() instanceof Occurrence.Optional) {
			return true;
		}
		if (e.occurrence() instanceof Occurrence.Repeated r && !(r.count() instanceof Expression.IntLiteral)) {
			return true;
		}
		return e instanceof FieldDescription f && f.length() != null && !(f.length() instanceof Expression.IntLiteral);
	}

	// ------------------------------------------------------------------ elements

	/**
	 * Writes an element as a component of {@code rec}.
	 *
	 * @param holderPath the path of the record holding it, when that isn't {@code rec}'s own (a top-level holder)
	 */
	private void element(ElementDescription e, Rec rec, String holderPath) {
		String name = eastName(e.name());
		String parent = holderPath != null ? holderPath : rec.path;
		String path = parent == null ? name : parent + "." + name;
		List<String> comments = semanticLines(e.semantics(), "");
		Occurrence o = e.occurrence();
		if (o instanceof Occurrence.Repeated r) {
			String element = constrainedType(e, path);
			String array = unique(name + "_ARRAY");
			index = true;
			types.append("   type ").append(array).append(" is array (AN_INDEX range <>) of ").append(element).append(";\n");
			String count = r.count() instanceof Expression.IntLiteral lit ? Long.toString(lit.value())
					: rec.virtual(name + "_COUNT", "AN_INDEX", "0", r.count(), e.id());
			rec.add(comments, name + " : " + array + " (1 .. " + count + ");");
			return;
		}
		if (o instanceof Occurrence.Optional opt) {
			Rec wrapper = new Rec(path);
			String inner = typeOf(e, wrapper, path + "." + name, e.id());
			String present = wrapper.virtual("PRESENT", "BOOLEAN", "TRUE", opt.condition(), e.id());
			String type = unique(name + "_OPTION");
			StringBuilder decl = new StringBuilder("   type ").append(type).append(discriminants(wrapper))
					.append(" is record\n      case ").append(present).append(" is\n");
			for (String c : semanticLines(e.semantics(), "")) {
				decl.append("         ").append(c).append('\n');
			}
			decl.append("         when TRUE => ").append(name).append(" : ").append(inner).append(";\n")
					.append("         when FALSE => null;\n      end case;\n   end record;\n");
			types.append(decl);
			rec.add(List.of(), name + " : " + type + ";");
			return;
		}
		rec.add(comments, name + " : " + typeOf(e, rec, path, e.id()) + ";");
	}

	/** The type of an element that occurs once, with its constraint; a computed length is a discriminant of {@code rec}. */
	private String typeOf(ElementDescription e, Rec rec, String path, String fromId) {
		pathOf.put(e.id(), path);
		String name = eastName(e.name());
		if (e instanceof RecordDescription r) {
			return recordType(r, path);
		}
		if (e instanceof ChoiceDescription c) {
			return choiceType(c, path);
		}
		FieldDescription f = (FieldDescription) e;
		if (f.type() == PrimitiveType.STRING || f.type() == PrimitiveType.BYTES) {
			String base = f.type() == PrimitiveType.STRING ? "STRING" : octets();
			if (f.length() == null) {
				throw refuse(e, "has no length");
			}
			String length = f.length() instanceof Expression.IntLiteral lit ? Long.toString(lit.value())
					: rec.virtual(name + "_LENGTH", "AN_INDEX", "0", f.length(), fromId);
			if (f.length() instanceof Expression.IntLiteral lit && lit.value() == 0) {
				throw refuse(e, "has no characters or bytes, which EAST can't describe");
			}
			return base + " (1 .. " + length + ")";
		}
		return scalarType(f);
	}

	/** The type of each element of a repeated element: a type name with no constraint left to give. */
	private String constrainedType(ElementDescription e, String path) {
		pathOf.put(e.id(), path);
		String name = eastName(e.name());
		if (e instanceof RecordDescription r) {
			return recordType(r, path);
		}
		if (e instanceof ChoiceDescription c) {
			return choiceType(c, path);
		}
		FieldDescription f = (FieldDescription) e;
		if (f.type() == PrimitiveType.STRING || f.type() == PrimitiveType.BYTES) {
			if (!(f.length() instanceof Expression.IntLiteral lit) || lit.value() < 1) {
				throw refuse(e, "is repeated text or bytes whose length is computed, which EAST can't describe");
			}
			String subtype = unique(name + (f.type() == PrimitiveType.STRING ? "_TEXT" : "_OCTETS"));
			types.append("   subtype ").append(subtype).append(" is ")
					.append(f.type() == PrimitiveType.STRING ? "STRING" : octets()).append(" (1 .. ")
					.append(lit.value()).append(");\n");
			return subtype;
		}
		return scalarType(f);
	}

	private String octets() {
		octets = true;
		index = true;
		return "OCTETS";
	}

	private String recordType(RecordDescription r, String path) {
		if (r.isText()) {
			throw refuse(r, "is delimited text, which EAST can't describe");
		}
		if (r.offset() != null) {
			throw refuse(r, "is read at an offset, which EAST can't describe");
		}
		if (r.compression() != null) {
			throw refuse(r, "is compressed, which EAST can't describe");
		}
		Long size = null;
		if (r.size() != null) {
			if (!(r.size() instanceof Expression.IntLiteral lit)) {
				throw refuse(r, "has a computed size, which EAST can't describe");
			}
			size = lit.value() * 8;
		}
		Rec rec = new Rec(path);
		for (ElementDescription child : r.children()) {
			if (child.occurrence() instanceof Occurrence.UntilEnd) {
				throw refuse(child, "repeats to the end of the data inside '" + r.name()
						+ "'; in EAST only the last variable can");
			}
			element(child, rec, null);
		}
		return record(unique(eastName(r.name()) + "_TYPE"), rec, size, List.of());
	}

	/** Declares a record type with the components and discriminants gathered in {@code rec}. */
	private String record(String type, Rec rec, Long sizeInBits, List<String> comments) {
		StringBuilder decl = new StringBuilder();
		for (String c : comments) {
			decl.append(c).append('\n');
		}
		decl.append("   type ").append(type).append(discriminants(rec)).append(" is record\n");
		if (rec.lines.isEmpty()) {
			decl.append("      null;\n");
		}
		for (String line : rec.lines) {
			decl.append("      ").append(line).append('\n');
		}
		decl.append("   end record;\n");
		if (sizeInBits != null) {
			decl.append("   for ").append(type).append("'size use ").append(sizeInBits).append(";\n");
		}
		types.append(decl);
		return type;
	}

	private String choiceType(ChoiceDescription c, String path) {
		Rec wrapper = new Rec(path);
		key = true;
		for (ChoiceDescription.Branch b : c.branches()) {
			if (!b.isIntegerKey()) {
				throw refuse(c, "is chosen by text ('" + b.key() + "'); an EAST variant is chosen by a number");
			}
		}
		String discriminant = wrapper.virtual("KEY", "A_KEY", "0", c.discriminator(), c.id());
		StringBuilder alternatives = new StringBuilder();
		for (ChoiceDescription.Branch b : c.branches()) {
			String branch = eastName(b.record().name());
			String branchPath = path + "." + branch;
			pathOf.put(b.record().id(), branchPath);
			String type = recordType(b.record(), branchPath);
			alternatives.append("         when ").append(new BigInteger(b.key().strip())).append(" => ").append(branch)
					.append(" : ").append(type).append(";\n");
		}
		String type = unique(eastName(c.name()) + "_CHOICE");
		types.append("   type ").append(type).append(discriminants(wrapper)).append(" is record\n      case ")
				.append(discriminant).append(" is\n").append(alternatives)
				.append("         when others => null;\n      end case;\n   end record;\n");
		return type;
	}

	private static String discriminants(Rec rec) {
		return rec.discriminants.isEmpty() ? "" : " (" + String.join("; ", rec.discriminants) + ")";
	}

	// ------------------------------------------------------------------ scalars

	private String scalarType(FieldDescription f) {
		PrimitiveType t = f.type();
		Semantics s = f.semantics();
		long bits = t == PrimitiveType.BITS ? ((Expression.IntLiteral) f.length()).value() : t.fixedWidth() * 8L;
		ByteOrder fieldOrder = f.byteOrder() == null || t.fixedWidth() <= 1 ? order : f.byteOrder();
		String orderSuffix = fieldOrder == order ? "" : fieldOrder == ByteOrder.LITTLE_ENDIAN ? "_LITTLE" : "_BIG";
		if (t.isFloat()) {
			String digits = bits == 32 ? "6" : "15";
			String range = range(s);
			String name;
			if (range.isEmpty()) {
				name = shared.computeIfAbsent("real" + bits + orderSuffix, k -> declare(unique("REAL_" + bits + orderSuffix),
						" is digits " + digits + ";", bits));
			} else {
				name = declare(unique(eastName(f.name()) + "_RANGE"), " is digits " + digits + range + ";", bits);
			}
			reps.put(name, new String[] {"REAL_PHYSICAL_DESCRIPTION", realRep(bits, fieldOrder)});
			return name;
		}
		Map<BigInteger, String> codes = integerCodes(s);
		if (codes != null) {
			String name = unique(eastName(f.name()) + "_CODE");
			Map<String, BigInteger> literals = new LinkedHashMap<>();
			Set<String> used = new HashSet<>();
			for (Map.Entry<BigInteger, String> c : codes.entrySet()) {
				String literal = literal(c.getValue(), c.getKey(), used);
				literals.put(literal, c.getKey());
			}
			StringBuilder decl = new StringBuilder("   type ").append(name).append(" is (")
					.append(String.join(", ", literals.keySet())).append(");\n   for ").append(name).append(" use (");
			List<String> clauses = new ArrayList<>();
			literals.forEach((l, c) -> clauses.add(l + " => " + c));
			decl.append(String.join(", ", clauses)).append(");\n   for ").append(name).append("'size use ").append(bits)
					.append(";\n");
			types.append(decl);
			integerRep(name, bits, t.isSigned(), fieldOrder, orderSuffix);
			return name;
		}
		BigInteger lo = t.isSigned() ? BigInteger.ONE.shiftLeft((int) bits - 1).negate() : BigInteger.ZERO;
		BigInteger hi = t.isSigned() ? BigInteger.ONE.shiftLeft((int) bits - 1).subtract(BigInteger.ONE)
				: BigInteger.ONE.shiftLeft((int) bits).subtract(BigInteger.ONE);
		String name;
		if (s.validMin() != null && s.validMax() != null && isWhole(s.validMin()) && isWhole(s.validMax())
				&& s.scale() == null && s.offset() == null && s.validMin().toBigInteger().compareTo(lo) >= 0
				&& s.validMax().toBigInteger().compareTo(hi) <= 0) {
			name = declare(unique(eastName(f.name()) + "_RANGE"), " is range " + s.validMin().toBigInteger() + " .. "
					+ s.validMax().toBigInteger() + ";", bits);
		} else {
			String base = (t == PrimitiveType.BITS ? "BITS_" : t.isSigned() ? "INTEGER_" : "UNSIGNED_") + bits
					+ orderSuffix;
			BigInteger low = lo;
			BigInteger high = hi;
			name = shared.computeIfAbsent(base, k -> declare(unique(base), " is range " + low + " .. " + high + ";", bits));
		}
		integerRep(name, bits, t.isSigned(), fieldOrder, orderSuffix);
		return name;
	}

	private String declare(String name, String definition, long bits) {
		types.append("   type ").append(name).append(definition).append("\n   for ").append(name).append("'size use ")
				.append(bits).append(";\n");
		return name;
	}

	/** An integer whose byte order isn't the description's own gets its octets' order as subfields. */
	private void integerRep(String type, long bits, boolean signed, ByteOrder fieldOrder, String orderSuffix) {
		if (orderSuffix.isEmpty() || bits % 8 != 0) {
			return;
		}
		int octetCount = (int) (bits / 8);
		List<String> subfields = new ArrayList<>();
		if (fieldOrder == ByteOrder.BIG_ENDIAN) {
			subfields.add("1 => (0, " + (bits - 1) + ")");
		} else {
			for (int i = 0; i < octetCount; i++) {
				int octet = octetCount - 1 - i;
				subfields.add((i + 1) + " => (" + octet * 8 + ", " + (octet * 8 + 7) + ")");
			}
		}
		reps.put(type, new String[] {"INTEGER_PHYSICAL_DESCRIPTION", "(NUMBER_OF_SUBFIELDS => " + subfields.size()
				+ ", COMPLEMENT => " + (signed ? "TWOS_COMPLEMENT" : "UNSIGNED") + ", LOCATION => ("
				+ String.join(", ", subfields) + "))"});
	}

	/** IEEE 754 (FCSTC000), laid out as CCSDS 646.0-G-1 shows for big- and little-endian machines. */
	private static String realRep(long bits, ByteOrder fieldOrder) {
		boolean single = bits == 32;
		String location;
		int sign;
		if (fieldOrder == ByteOrder.BIG_ENDIAN) {
			sign = 0;
			location = single ? "LOCATION_OF_EXPONENT => (1 => (1, 8)), LOCATION_OF_MANTISSA => (1 => (9, 31))"
					: "LOCATION_OF_EXPONENT => (1 => (1, 11)), LOCATION_OF_MANTISSA => (1 => (12, 63))";
		} else if (single) {
			sign = 24;
			location = "LOCATION_OF_EXPONENT => (1 => (25, 31), 2 => (16, 16)), "
					+ "LOCATION_OF_MANTISSA => (1 => (17, 23), 2 => (8, 15), 3 => (0, 7))";
		} else {
			sign = 56;
			location = "LOCATION_OF_EXPONENT => (1 => (57, 63), 2 => (48, 51)), LOCATION_OF_MANTISSA => (1 => (52, 55), "
					+ "2 => (40, 47), 3 => (32, 39), 4 => (24, 31), 5 => (16, 23), 6 => (8, 15), 7 => (0, 7))";
		}
		int exponentParts = fieldOrder == ByteOrder.BIG_ENDIAN ? 1 : 2;
		int mantissaParts = fieldOrder == ByteOrder.BIG_ENDIAN ? 1 : single ? 3 : 7;
		return "(NUMBER_OF_SUBFIELDS_IN_EXPONENT => " + exponentParts + ", NUMBER_OF_SUBFIELDS_IN_MANTISSA => "
				+ mantissaParts + ", CONVENTION_USED => FCSTC000, SIGN_BIT_NUMBER => " + sign
				+ ", COMPLEMENT => SIGN_AND_MAGNITUDE, EXPONENT_BASE => 2, BIAS => " + (single ? 127 : 1023) + ", "
				+ location + ")";
	}

	private static String range(Semantics s) {
		if (s.validMin() == null || s.validMax() == null || s.scale() != null || s.offset() != null) {
			return "";
		}
		return " range " + real(s.validMin()) + " .. " + real(s.validMax());
	}

	private static String real(BigDecimal d) {
		String text = d.toPlainString();
		return text.contains(".") ? text : text + ".0";
	}

	/** The codes of a field's code list, if they're all whole numbers. */
	private static Map<BigInteger, String> integerCodes(Semantics s) {
		if (s.codes().isEmpty()) {
			return null;
		}
		Map<BigInteger, String> codes = new TreeMap<>();
		for (Map.Entry<String, String> c : s.codes().entrySet()) {
			try {
				codes.put(new BigInteger(c.getKey().strip()), c.getValue());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return codes;
	}

	/** An enumeration literal for a code's meaning. */
	private static String literal(String meaning, BigInteger code, Set<String> used) {
		String base = eastName(meaning == null || meaning.isBlank() ? "CODE_" + code : meaning);
		String name = base;
		for (int i = 2; !used.add(name); i++) {
			name = base + "_" + i;
		}
		return name;
	}

	// ------------------------------------------------------------------ physical package

	private void physical(StringBuilder out, String name) {
		out.append("package ").append(name).append(" is\n");
		if (order == ByteOrder.LITTLE_ENDIAN) {
			out.append("   type BIT_ORDER is (HIGH_ORDER_FIRST, LOW_ORDER_FIRST);\n");
			out.append("   OCTET_STORAGE : constant BIT_ORDER := LOW_ORDER_FIRST;\n");
		}
		if (!reps.isEmpty()) {
			out.append("""
					   type NATURAL_NUMBER is range 0 .. 65535;
					   type LOCATION_OF_SUBFIELD is record
					      BEGINNING_AT_BIT_NUMBER : NATURAL_NUMBER;
					      ENDING_AT_BIT_NUMBER : NATURAL_NUMBER;
					   end record;
					   MAXIMUM_NUMBER_OF_SUBFIELDS : constant := 255;
					   type SUBFIELD_NUMBER is range 1 .. MAXIMUM_NUMBER_OF_SUBFIELDS;
					   type LOCATION_OF_FIELD is array (SUBFIELD_NUMBER range <>) of LOCATION_OF_SUBFIELD;
					   type SIGN_CONVENTION is (UNSIGNED, SIGN_AND_MAGNITUDE, ONES_COMPLEMENT, TWOS_COMPLEMENT);
					""");
			boolean integers = reps.values().stream().anyMatch(r -> r[0].equals("INTEGER_PHYSICAL_DESCRIPTION"));
			boolean reals = reps.values().stream().anyMatch(r -> r[0].equals("REAL_PHYSICAL_DESCRIPTION"));
			if (integers) {
				out.append("""
						   type INTEGER_PHYSICAL_DESCRIPTION (NUMBER_OF_SUBFIELDS : SUBFIELD_NUMBER := 1) is record
						      COMPLEMENT : SIGN_CONVENTION;
						      LOCATION : LOCATION_OF_FIELD (1 .. NUMBER_OF_SUBFIELDS);
						   end record;
						""");
			}
			if (reals) {
				out.append("""
						   type LIST_OF_RECOGNIZED_CONVENTIONS is (FCSTC000);
						   type REAL_PHYSICAL_DESCRIPTION (NUMBER_OF_SUBFIELDS_IN_EXPONENT : SUBFIELD_NUMBER := 1;
						         NUMBER_OF_SUBFIELDS_IN_MANTISSA : SUBFIELD_NUMBER := 1) is record
						      CONVENTION_USED : LIST_OF_RECOGNIZED_CONVENTIONS;
						      SIGN_BIT_NUMBER : NATURAL_NUMBER;
						      COMPLEMENT : SIGN_CONVENTION;
						      EXPONENT_BASE : NATURAL_NUMBER;
						      BIAS : NATURAL_NUMBER;
						      LOCATION_OF_EXPONENT : LOCATION_OF_FIELD (1 .. NUMBER_OF_SUBFIELDS_IN_EXPONENT);
						      LOCATION_OF_MANTISSA : LOCATION_OF_FIELD (1 .. NUMBER_OF_SUBFIELDS_IN_MANTISSA);
						   end record;
						""");
			}
			List<String> userTypes = new ArrayList<>();
			StringBuilder relation = new StringBuilder();
			for (Map.Entry<String, String[]> rep : reps.entrySet()) {
				String type = rep.getKey();
				out.append("   ").append(type).append("_REPRESENTATION : constant ").append(rep.getValue()[0])
						.append(" :=\n      ").append(rep.getValue()[1]).append(";\n");
				userTypes.add("USER_TYPE_" + type);
				relation.append("         when USER_TYPE_").append(type).append(" => PHYS_").append(type).append(" : ")
						.append(rep.getValue()[0]).append(" := ").append(type).append("_REPRESENTATION;\n");
			}
			out.append("   type BASIC_TYPE_NAMES is (").append(String.join(", ", userTypes)).append(");\n");
			out.append("   type RELATION (CHOICE : BASIC_TYPE_NAMES) is record\n      case CHOICE is\n")
					.append(relation).append("      end case;\n   end record;\n");
		}
		out.append("end ").append(name).append(";\n");
	}

	// ------------------------------------------------------------------ expressions and names

	/** An expression in EAST, with each field it uses named by its EAST path. */
	private String render(Expression e, String fromId) {
		return e.render(new Expression.Syntax() {
			@Override
			public String intLiteral(long value) {
				return value < 0 ? "(" + value + ")" : Long.toString(value);
			}

			@Override
			public String stringLiteral(String value) {
				return "\"" + value.replace("\"", "\"\"") + "\"";
			}

			@Override
			public String fieldRef(FieldRef ref) {
				Scope.Resolved r = scope.resolve(fromId, ref.name()).orElseThrow(() -> new EastException(0,
						"'" + ref.name() + "' isn't a field read earlier - validate the description first"));
				String path = pathOf.get(r.target().id());
				if (path == null) {
					throw new EastException(0, "'" + ref.name() + "' hasn't been written yet");
				}
				return path;
			}

			@Override
			public String binary(Op op) {
				return switch (op) {
					case OR -> "or";
					case AND -> "and";
					case EQ -> "=";
					case NE -> "/=";
					case LT -> "<";
					case LE -> "<=";
					case GT -> ">";
					case GE -> ">=";
					case ADD -> "+";
					case SUB -> "-";
					case MUL -> "*";
					case DIV -> "/";
					case MOD -> "rem";
					default -> throw new IllegalArgumentException(op.toString());
				};
			}

			@Override
			public String unary(Op op, String operand) {
				return op == Op.NOT ? "(not " + operand + ")" : "(-" + operand + ")";
			}
		});
	}

	/** An EAST identifier for a name: upper case, letters, digits and single underscores, not a keyword. */
	public static String eastName(String name) {
		String n = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("_+", "_")
				.replaceAll("^_|_$", "");
		if (n.isEmpty()) {
			n = "X";
		}
		if (!Character.isLetter(n.charAt(0))) {
			n = "N_" + n;
		}
		if (RESERVED.contains(n) || n.startsWith("VIRTUAL_")) {
			n = n + "_1";
		}
		return n;
	}

	private String unique(String base) {
		String name = base;
		for (int i = 2; !names.add(name); i++) {
			name = base + "_" + i;
		}
		return name;
	}

	/** Comments giving an element's meaning. */
	private static List<String> semanticLines(Semantics s, String indent) {
		if (s == null || s.isEmpty()) {
			return List.of();
		}
		List<String> parts = new ArrayList<>();
		StringBuilder head = new StringBuilder();
		if (s.semanticName() != null) {
			head.append(s.semanticName());
		}
		if (s.units() != null) {
			head.append(head.length() > 0 ? " " : "").append("[").append(s.units()).append("]");
		}
		if (s.definition() != null) {
			head.append(head.length() > 0 ? ": " : "").append(s.definition());
		}
		if (head.length() > 0) {
			parts.add(head.toString());
		}
		if (s.unitsUri() != null) {
			parts.add("Units: " + s.unitsUri());
		}
		if (s.conceptUri() != null) {
			parts.add("Concept: " + s.conceptUri());
		}
		if (s.scale() != null || s.offset() != null) {
			parts.add("Value = raw * " + (s.scale() == null ? "1" : s.scale().toPlainString()) + " + "
					+ (s.offset() == null ? "0" : s.offset().toPlainString()));
		}
		if (s.fillValue() != null) {
			parts.add("Fill value (no data): " + s.fillValue());
		}
		if (!s.codes().isEmpty()) {
			List<String> codes = new ArrayList<>();
			s.codes().forEach((c, m) -> codes.add(c + " = " + m));
			parts.add("Codes: " + String.join("; ", codes));
		}
		if ((s.validMin() != null || s.validMax() != null) && (s.scale() != null || s.offset() != null)) {
			parts.add("Valid values: " + (s.validMin() == null ? "" : s.validMin().toPlainString()) + " .. "
					+ (s.validMax() == null ? "" : s.validMax().toPlainString()));
		}
		List<String> lines = new ArrayList<>();
		for (String part : parts) {
			for (String line : wrap(part, 72)) {
				lines.add(indent + "-- " + line);
			}
		}
		return lines;
	}

	private static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		for (String paragraph : text.split("\\R")) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.strip().split("\\s+")) {
				if (line.length() > 0 && line.length() + 1 + word.length() > width) {
					lines.add(line.toString());
					line.setLength(0);
				}
				line.append(line.length() > 0 ? " " : "").append(word);
			}
			if (line.length() > 0) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	private static boolean isWhole(BigDecimal d) {
		return d.signum() == 0 || d.stripTrailingZeros().scale() <= 0;
	}

	private EastException refuse(ElementDescription e, String why) {
		return new EastException(0, "'" + e.name() + "' " + why + ".");
	}
}
