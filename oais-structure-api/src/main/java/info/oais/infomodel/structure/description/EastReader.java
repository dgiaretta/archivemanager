package info.oais.infomodel.structure.description;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a Data Description Record written in EAST (CCSDS 644.0-B-3, "The
 * Data Description Language EAST Specification") as a
 * {@link FormatDescription}, so the RepInfo Tools can generate Kaitai Struct,
 * DFDL and DRB descriptions from it and test them against sample data.
 *
 * <p>An EAST description has a logical package (types, representation
 * clauses, then the variables in the order the data holds them) and a
 * physical package (byte order, array storage, and how numbers are
 * represented). The description model has no named types, so each variable
 * is expanded into elements: records into records, arrays into repeated
 * elements, enumerations into integer fields whose {@link Semantics#codes()}
 * name each literal, and integer and real ranges into
 * {@link Semantics#validMin()} and {@link Semantics#validMax()}. Record
 * representation clauses fix the order of components and the unused space
 * between them, which becomes {@code spare_n} fields. A variant part becomes
 * a {@link ChoiceDescription} when each alternative is chosen by a single
 * value, and otherwise one optional record per alternative, present when its
 * condition holds (which covers {@code |}, ranges, {@code others},
 * {@code null} alternatives and true/false discriminants). Virtual
 * discriminants are replaced by the expressions their actual values give.</p>
 *
 * <p>A description describes one set of data; unless the last variable is
 * repeated until an EOF marker, the sets repeat to the end of the data, in
 * a record called {@code set}.</p>
 *
 * <p>What the description model can't express is refused with an
 * {@link EastException} giving the line and why: markers other than EOF,
 * references into a record read earlier ({@code LAST_DATE.DAY} from outside
 * {@code LAST_DATE}), the {@code **} operator and functions on data values,
 * signed or least-significant-bit-first bit fields, integers in pieces or
 * not in two's complement, and reals in conventions other than IEEE 754.
 * Numbers in an ASCII representation are read as text, saying so in their
 * definition.</p>
 */
public final class EastReader {

	/** Why an EAST description can't be read, and where. */
	public static final class EastException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		private final int line;

		EastException(int line, String message) {
			super(line > 0 ? "Line " + line + ": " + message : message);
			this.line = line;
		}

		/** @return the line of the description the problem is on; 0 if it isn't on one line */
		public int line() {
			return line;
		}
	}

	/**
	 * @param text an EAST Data Description Record: the logical package, then the physical package
	 * @return the description it gives
	 * @throws EastException if it can't be read, or uses what the description model can't express
	 */
	public static FormatDescription read(String text) {
		EastReader reader = new EastReader(text);
		Pkg logical = reader.parsePackage();
		Pkg physical = reader.at(K.END) ? null : reader.parsePackage();
		if (!reader.at(K.END)) {
			throw reader.error("Expected the end of the description after the physical package");
		}
		return new Builder(logical, physical).build();
	}

	// ------------------------------------------------------------------ lexer

	private enum K {
		IDENT, NUM, STRING, CHAR, SYM, END
	}

	/** A token; {@code s} is lower case for identifiers, {@code raw} as written. */
	private record Tok(K k, String s, String raw, BigDecimal num, boolean real, int line) {
	}

	private static final Set<String> TWO_CHAR = Set.of("=>", "..", "**", ":=", "/=", ">=", "<=", "<>");

	private final List<Tok> toks;
	private int pos;

	private EastReader(String text) {
		this.toks = lex(text.replace('‘', '\'').replace('’', '\'').replace('“', '"')
				.replace('”', '"'));
	}

	private static List<Tok> lex(String text) {
		List<Tok> out = new ArrayList<>();
		int i = 0;
		int line = 1;
		int n = text.length();
		while (i < n) {
			char c = text.charAt(i);
			if (c == '\n') {
				line++;
				i++;
			} else if (Character.isWhitespace(c) || c < ' ') {
				i++;
			} else if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
				while (i < n && text.charAt(i) != '\n') {
					i++;
				}
			} else if (Character.isLetter(c)) {
				int start = i;
				while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
					i++;
				}
				String raw = text.substring(start, i);
				out.add(new Tok(K.IDENT, raw.toLowerCase(Locale.ROOT), raw, null, false, line));
			} else if (Character.isDigit(c)) {
				i = number(text, i, line, out);
			} else if (c == '"') {
				StringBuilder sb = new StringBuilder();
				i++;
				while (true) {
					if (i >= n || text.charAt(i) == '\n') {
						throw new EastException(line, "A string isn't closed with \" on the same line");
					}
					if (text.charAt(i) == '"') {
						if (i + 1 < n && text.charAt(i + 1) == '"') {
							sb.append('"');
							i += 2;
							continue;
						}
						i++;
						break;
					}
					sb.append(text.charAt(i++));
				}
				out.add(new Tok(K.STRING, sb.toString(), sb.toString(), null, false, line));
			} else if ((c == '\'' || c == '`') && i + 2 < n && text.charAt(i + 2) == '\''
					&& !(c == '\'' && afterName(out))) {
				String ch = String.valueOf(text.charAt(i + 1));
				out.add(new Tok(K.CHAR, ch, ch, null, false, line));
				i += 3;
			} else if (i + 1 < n && TWO_CHAR.contains(text.substring(i, i + 2))) {
				out.add(new Tok(K.SYM, text.substring(i, i + 2), text.substring(i, i + 2), null, false, line));
				i += 2;
			} else if ("&'()*+,-./:;<=>|!".indexOf(c) >= 0) {
				out.add(new Tok(K.SYM, String.valueOf(c), String.valueOf(c), null, false, line));
				i++;
			} else {
				throw new EastException(line, "Unexpected character '" + c + "'");
			}
		}
		out.add(new Tok(K.END, "", "", null, false, line));
		return out;
	}

	/** Whether a {@code '} here follows a name, so is an attribute ({@code T'size}), not a character. */
	private static boolean afterName(List<Tok> out) {
		if (out.isEmpty()) {
			return false;
		}
		Tok last = out.get(out.size() - 1);
		return last.k() == K.IDENT || last.s().equals(")");
	}

	/** Reads a decimal or based ({@code 16#FF#}) literal; returns where it ends. */
	private static int number(String text, int i, int line, List<Tok> out) {
		int n = text.length();
		int start = i;
		StringBuilder digits = new StringBuilder();
		while (i < n && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '_')) {
			if (text.charAt(i) != '_') {
				digits.append(text.charAt(i));
			}
			i++;
		}
		BigDecimal value;
		boolean real = false;
		int base = 10;
		if (i < n && text.charAt(i) == '#') {
			base = Integer.parseInt(digits.toString());
			if (base != 2 && base != 8 && base != 16) {
				throw new EastException(line, "A based literal's base must be 2, 8 or 16, not " + base);
			}
			i++;
			StringBuilder whole = new StringBuilder();
			StringBuilder fraction = null;
			while (i < n && text.charAt(i) != '#') {
				char c = text.charAt(i++);
				if (c == '_') {
					continue;
				}
				if (c == '.') {
					fraction = new StringBuilder();
					real = true;
				} else if (Character.digit(c, base) >= 0) {
					(fraction != null ? fraction : whole).append(c);
				} else {
					throw new EastException(line, "'" + c + "' isn't a digit in base " + base);
				}
			}
			if (i >= n) {
				throw new EastException(line, "A based literal isn't closed with #");
			}
			i++;
			value = new BigDecimal(new BigInteger(whole.length() == 0 ? "0" : whole.toString(), base));
			if (fraction != null && fraction.length() > 0) {
				BigDecimal f = new BigDecimal(new BigInteger(fraction.toString(), base))
						.divide(BigDecimal.valueOf(base).pow(fraction.length()));
				value = value.add(f);
			}
		} else {
			StringBuilder all = new StringBuilder(digits);
			if (i + 1 < n && text.charAt(i) == '.' && Character.isDigit(text.charAt(i + 1))) {
				real = true;
				all.append('.');
				i++;
				while (i < n && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '_')) {
					if (text.charAt(i) != '_') {
						all.append(text.charAt(i));
					}
					i++;
				}
			}
			value = new BigDecimal(all.toString());
		}
		if (i < n && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
			int j = i + 1;
			boolean negative = false;
			if (j < n && (text.charAt(j) == '+' || text.charAt(j) == '-')) {
				negative = text.charAt(j) == '-';
				j++;
			}
			if (j < n && Character.isDigit(text.charAt(j))) {
				int e = 0;
				while (j < n && Character.isDigit(text.charAt(j))) {
					e = e * 10 + (text.charAt(j++) - '0');
				}
				BigDecimal scale = BigDecimal.valueOf(base).pow(e);
				value = negative ? value.divide(scale) : value.multiply(scale);
				i = j;
			}
		}
		out.add(new Tok(K.NUM, text.substring(start, i), text.substring(start, i), value, real, line));
		return i;
	}

	// ------------------------------------------------------------------ parser helpers

	private Tok peek() {
		return toks.get(pos);
	}

	private Tok peek(int ahead) {
		return toks.get(Math.min(pos + ahead, toks.size() - 1));
	}

	private Tok next() {
		Tok t = toks.get(pos);
		if (t.k() != K.END) {
			pos++;
		}
		return t;
	}

	private boolean at(K k) {
		return peek().k() == k;
	}

	/** Whether the next token is this symbol or keyword. */
	private boolean is(String s) {
		Tok t = peek();
		return (t.k() == K.SYM || t.k() == K.IDENT) && t.s().equals(s);
	}

	private boolean accept(String s) {
		if (is(s)) {
			pos++;
			return true;
		}
		return false;
	}

	private Tok expect(String s) {
		if (!is(s)) {
			throw error("Expected '" + s + "'");
		}
		return next();
	}

	private Tok ident() {
		if (!at(K.IDENT)) {
			throw error("Expected a name");
		}
		return next();
	}

	private EastException error(String message) {
		Tok t = peek();
		return new EastException(t.line(), message + (t.k() == K.END ? " but the description ended"
				: ", not '" + t.raw() + "'"));
	}

	// ------------------------------------------------------------------ syntax tree

	private sealed interface Node permits Num, Str, Chr, Name, Bin, Un, Call, Agg {
	}

	private record Num(BigDecimal value, boolean real) implements Node {
	}

	private record Str(String value) implements Node {
	}

	private record Chr(char value) implements Node {
	}

	/** A name or an EAST path ({@code PACKET.PRIMARY_HEADER.LENGTH}), lower case, with the spelling as written. */
	private record Name(List<String> path, String raw, int line) implements Node {
		String joined() {
			return String.join(".", path);
		}

		String last() {
			return path.get(path.size() - 1);
		}
	}

	private record Bin(String op, Node left, Node right, int line) implements Node {
	}

	private record Un(String op, Node operand, int line) implements Node {
	}

	private record Call(String function, List<Node> args, int line) implements Node {
	}

	/** An aggregate: {@code (a, b)} or {@code (X => a, others => b)}; keys are null for positional items. */
	private record Agg(List<Item> items) implements Node {
		record Item(List<Node> keys, boolean others, Node value) {
		}

		Node get(String name, int index) {
			for (Item item : items) {
				if (item.keys() != null && item.keys().size() == 1 && item.keys().get(0) instanceof Name n
						&& n.joined().equals(name)) {
					return item.value();
				}
			}
			return index < items.size() && items.get(index).keys() == null ? items.get(index).value() : null;
		}
	}

	/** One index range, or the discrete type giving it; {@code typeName} with no bounds is the whole type. */
	private record Index(String typeName, Node lo, Node hi, boolean unconstrained) {
	}

	/** A value, or range of values, a variant alternative is chosen by; or {@code others}. */
	private record Choice(Node value, Node hi, boolean others) {
	}

	private abstract static class Ty {
		final String name;
		final int line;
		Long size;

		Ty(String name, int line) {
			this.name = name;
			this.line = line;
		}
	}

	private static final class EnumTy extends Ty {
		final List<String> literals;
		final List<String> raw;
		Map<String, BigInteger> codes;

		EnumTy(String name, int line, List<String> literals, List<String> raw) {
			super(name, line);
			this.literals = literals;
			this.raw = raw;
		}

		BigInteger code(String literal) {
			int i = literals.indexOf(literal);
			if (i < 0) {
				return null;
			}
			return codes != null ? codes.get(literal) : BigInteger.valueOf(i);
		}
	}

	private static final class IntTy extends Ty {
		final Node lo;
		final Node hi;

		IntTy(String name, int line, Node lo, Node hi) {
			super(name, line);
			this.lo = lo;
			this.hi = hi;
		}
	}

	private static final class RealTy extends Ty {
		final Node lo;
		final Node hi;

		RealTy(String name, int line, Node lo, Node hi) {
			super(name, line);
			this.lo = lo;
			this.hi = hi;
		}
	}

	private static final class ArrTy extends Ty {
		final List<Index> indices;
		final String element;

		ArrTy(String name, int line, List<Index> indices, String element) {
			super(name, line);
			this.indices = indices;
			this.element = element;
		}

		boolean unconstrained() {
			return indices.stream().anyMatch(Index::unconstrained);
		}
	}

	private record Discr(String name, String type, int line) {
		boolean virtual() {
			return name.startsWith("virtual_");
		}
	}

	private record Comp(String name, String type, List<Index> constraint, Node value, boolean constant, int line) {
	}

	private record When(List<Choice> choices, List<Comp> comps, Variant nested, int line) {
	}

	private record Variant(String discriminant, List<When> whens, int line) {
		void names(List<String> into) {
			for (When w : whens) {
				w.comps().forEach(c -> into.add(c.name()));
				if (w.nested() != null) {
					w.nested().names(into);
				}
			}
		}
	}

	private static final class RecTy extends Ty {
		final List<Discr> discriminants;
		final List<Comp> comps;
		final Variant variant;
		Map<String, long[]> layout = Map.of();

		RecTy(String name, int line, List<Discr> discriminants, List<Comp> comps, Variant variant) {
			super(name, line);
			this.discriminants = discriminants;
			this.comps = comps;
			this.variant = variant;
		}
	}

	private static final class SubTy extends Ty {
		final String base;
		final List<Index> constraint;
		final Node lo;
		final Node hi;

		SubTy(String name, int line, String base, List<Index> constraint, Node lo, Node hi) {
			super(name, line);
			this.base = base;
			this.constraint = constraint;
			this.lo = lo;
			this.hi = hi;
		}
	}

	private record Const(String type, Node value, int line) {
	}

	private record Var(String name, String type, List<Index> constraint, int line) {
	}

	private static final class Pkg {
		String name;
		String raw;
		final Map<String, Ty> types = new LinkedHashMap<>();
		final Map<String, Const> constants = new LinkedHashMap<>();
		final List<Var> vars = new ArrayList<>();
		final Map<String, Node> actuals = new HashMap<>();
		final Map<String, Integer> actualLines = new HashMap<>();
		boolean untilEof;
		String version;
	}

	// ------------------------------------------------------------------ declarations

	private Pkg parsePackage() {
		expect("package");
		Pkg pkg = new Pkg();
		Tok name = ident();
		pkg.name = name.s();
		pkg.raw = name.raw();
		expect("is");
		while (!is("end")) {
			if (at(K.END)) {
				throw error("Expected 'end " + pkg.raw + ";'");
			}
			declaration(pkg);
		}
		expect("end");
		if (at(K.IDENT)) {
			next();
		}
		expect(";");
		return pkg;
	}

	private void declaration(Pkg pkg) {
		int line = peek().line();
		if (accept("type")) {
			Ty t = typeDeclaration(line);
			define(pkg, t);
		} else if (accept("subtype")) {
			String name = ident().s();
			expect("is");
			String base = ident().s();
			List<Index> constraint = null;
			Node lo = null;
			Node hi = null;
			if (accept("digits") || accept("digit")) {
				expr();
			}
			if (accept("range")) {
				lo = expr();
				expect("..");
				hi = expr();
			} else if (is("(")) {
				constraint = indexList();
			}
			expect(";");
			define(pkg, new SubTy(name, line, base, constraint, lo, hi));
		} else if (accept("for")) {
			representationClause(pkg, line);
		} else if (at(K.IDENT)) {
			objectDeclaration(pkg, line);
		} else {
			throw error("Expected a declaration");
		}
	}

	private void define(Pkg pkg, Ty t) {
		if (pkg.types.containsKey(t.name)) {
			throw new EastException(t.line, "'" + t.name.toUpperCase(Locale.ROOT) + "' is declared twice");
		}
		pkg.types.put(t.name, t);
	}

	private Ty typeDeclaration(int line) {
		String name = ident().s();
		List<Discr> discriminants = new ArrayList<>();
		if (accept("(")) {
			do {
				List<Tok> names = new ArrayList<>();
				names.add(ident());
				while (accept(",")) {
					names.add(ident());
				}
				expect(":");
				String type = ident().s();
				if (accept(":=")) {
					expr();
				}
				for (Tok n : names) {
					discriminants.add(new Discr(n.s(), type, n.line()));
				}
			} while (accept(";"));
			expect(")");
		}
		expect("is");
		if (accept("(")) {
			List<String> literals = new ArrayList<>();
			List<String> raw = new ArrayList<>();
			do {
				Tok t = next();
				if (t.k() == K.IDENT || t.k() == K.CHAR) {
					literals.add(t.k() == K.CHAR ? "'" + t.s() + "'" : t.s());
					raw.add(t.k() == K.CHAR ? "'" + t.raw() + "'" : t.raw());
				} else {
					pos--;
					throw error("Expected an enumeration literal");
				}
			} while (accept(","));
			expect(")");
			expect(";");
			return new EnumTy(name, line, literals, raw);
		}
		if (accept("range")) {
			Node lo = expr();
			expect("..");
			Node hi = expr();
			expect(";");
			return new IntTy(name, line, lo, hi);
		}
		if (accept("digits") || accept("digit")) {
			expr();
			Node lo = null;
			Node hi = null;
			if (accept("range")) {
				lo = expr();
				expect("..");
				hi = expr();
			}
			expect(";");
			return new RealTy(name, line, lo, hi);
		}
		if (accept("array")) {
			List<Index> indices = indexList();
			expect("of");
			String element = ident().s();
			expect(";");
			return new ArrTy(name, line, indices, element);
		}
		if (accept("record")) {
			List<Comp> comps = new ArrayList<>();
			Variant variant = recordBody(comps);
			expect("end");
			expect("record");
			expect(";");
			return new RecTy(name, line, discriminants, comps, variant);
		}
		throw error("Expected an enumeration, range, digits, array or record after 'is'");
	}

	/** Components up to {@code end} or {@code when}; returns the variant part, if any. */
	private Variant recordBody(List<Comp> comps) {
		Variant variant = null;
		while (!is("end") && !is("when")) {
			int line = peek().line();
			if (accept("null")) {
				expect(";");
			} else if (accept("case")) {
				if (variant != null) {
					throw new EastException(line, "A record has only one variant part");
				}
				variant = variantPart(line);
			} else if (at(K.IDENT)) {
				if (variant != null) {
					throw new EastException(line, "Components come before the variant part of a record");
				}
				List<Tok> names = new ArrayList<>();
				names.add(ident());
				while (accept(",")) {
					names.add(ident());
				}
				expect(":");
				boolean constant = accept("constant");
				String type = ident().s();
				List<Index> constraint = is("(") ? indexList() : null;
				Node value = accept(":=") ? expr() : null;
				expect(";");
				for (Tok n : names) {
					comps.add(new Comp(n.s(), type, constraint, value, constant, n.line()));
				}
			} else {
				throw error("Expected a component, 'case' or 'end record'");
			}
		}
		return variant;
	}

	private Variant variantPart(int line) {
		String discriminant = ident().s();
		expect("is");
		List<When> whens = new ArrayList<>();
		while (accept("when")) {
			int whenLine = peek().line();
			List<Choice> choices = new ArrayList<>();
			do {
				if (accept("others")) {
					choices.add(new Choice(null, null, true));
				} else {
					Node v = expr();
					choices.add(accept("..") ? new Choice(v, expr(), false) : new Choice(v, null, false));
				}
			} while (accept("|"));
			expect("=>");
			List<Comp> comps = new ArrayList<>();
			Variant nested = recordBody(comps);
			whens.add(new When(choices, comps, nested, whenLine));
		}
		expect("end");
		expect("case");
		expect(";");
		return new Variant(discriminant, whens, line);
	}

	/** {@code (index {, index})}, in an array type or an index constraint. */
	private List<Index> indexList() {
		expect("(");
		List<Index> indices = new ArrayList<>();
		do {
			Node first = expr();
			if (accept("..")) {
				indices.add(new Index(null, first, expr(), false));
			} else if (first instanceof Name n && n.path().size() == 1) {
				if (accept("range")) {
					if (accept("<>")) {
						indices.add(new Index(n.last(), null, null, true));
					} else {
						Node lo = expr();
						expect("..");
						indices.add(new Index(n.last(), lo, expr(), false));
					}
				} else {
					indices.add(new Index(n.last(), null, null, false));
				}
			} else {
				throw error("Expected an index range such as 1 .. 10");
			}
		} while (accept(","));
		expect(")");
		return indices;
	}

	private void representationClause(Pkg pkg, int line) {
		Tok type = ident();
		Ty t = pkg.types.get(type.s());
		if (t == null) {
			throw new EastException(type.line(), "'" + type.raw() + "' isn't declared before its representation clause");
		}
		if (accept("'")) {
			Tok attribute = ident();
			if (!attribute.s().equals("size")) {
				throw new EastException(attribute.line(), "Only the 'size attribute can be given, not '" + attribute.raw());
			}
			expect("use");
			Node size = expr();
			expect(";");
			BigDecimal bits = staticValue(pkg, size);
			if (bits == null || bits.signum() <= 0 || bits.stripTrailingZeros().scale() > 0) {
				throw new EastException(line, "The size of '" + type.raw() + "' must be a whole number of bits");
			}
			t.size = bits.longValueExact();
			return;
		}
		expect("use");
		if (accept("record")) {
			if (!(t instanceof RecTy r)) {
				throw new EastException(line, "'" + type.raw() + "' isn't a record type");
			}
			Map<String, long[]> layout = new LinkedHashMap<>();
			while (!is("end")) {
				Tok comp = ident();
				expect("at");
				Node distance = expr();
				expect("range");
				Node first = expr();
				expect("..");
				Node last = expr();
				expect(";");
				BigDecimal d = staticValue(pkg, distance);
				BigDecimal a = staticValue(pkg, first);
				BigDecimal b = staticValue(pkg, last);
				if (d == null || a == null || b == null) {
					throw new EastException(comp.line(), "The position of '" + comp.raw() + "' must be a number of bits");
				}
				long start = d.longValue() + a.longValue();
				long end = d.longValue() + b.longValue();
				if (end < start) {
					throw new EastException(comp.line(), "'" + comp.raw() + "' ends before it starts");
				}
				layout.put(comp.s(), new long[] {start, end, comp.line()});
			}
			expect("end");
			expect("record");
			expect(";");
			r.layout = layout;
			return;
		}
		Node value = expr();
		expect(";");
		if (!(t instanceof EnumTy e) || !(value instanceof Agg agg)) {
			throw new EastException(line, "Only an enumeration type can be given codes with 'for ... use (...)'");
		}
		Map<String, BigInteger> codes = new LinkedHashMap<>();
		BigInteger previous = null;
		for (Agg.Item item : agg.items()) {
			if (item.keys() == null || item.keys().size() != 1 || !(item.keys().get(0) instanceof Name n)
					|| !e.literals.contains(n.joined())) {
				throw new EastException(line, "Each code must be given as LITERAL => value for a literal of '"
						+ type.raw() + "'");
			}
			BigDecimal v = staticValue(pkg, item.value());
			if (v == null) {
				throw new EastException(line, "The code of " + n.raw() + " must be a whole number");
			}
			BigInteger code = v.toBigIntegerExact();
			if (previous != null && code.compareTo(previous) <= 0) {
				throw new EastException(line, "The codes of '" + type.raw() + "' must increase in the order of its literals");
			}
			previous = code;
			codes.put(n.joined(), code);
		}
		if (codes.size() != e.literals.size()) {
			throw new EastException(line, "Every literal of '" + type.raw() + "' needs a code");
		}
		Map<String, BigInteger> ordered = new LinkedHashMap<>();
		e.literals.forEach(l -> ordered.put(l, codes.get(l)));
		e.codes = ordered;
	}

	private void objectDeclaration(Pkg pkg, int line) {
		List<String> path = new ArrayList<>();
		StringBuilder raw = new StringBuilder();
		Tok first = ident();
		path.add(first.s());
		raw.append(first.raw());
		while (accept(".")) {
			Tok t = ident();
			path.add(t.s());
			raw.append('.').append(t.raw());
		}
		expect(":");
		String name = String.join(".", path);
		if (accept("virtual")) {
			ident();
			expect(":=");
			Node value = expr();
			expect(";");
			pkg.actuals.put(name, value);
			pkg.actualLines.put(name, line);
			return;
		}
		if (path.size() > 1) {
			throw new EastException(line, "Only an actual discriminant value ('" + raw + " : virtual ...') "
					+ "can be declared for a path");
		}
		if (accept("constant")) {
			String type = null;
			if (!is(":=")) {
				type = ident().s();
				if (is("(")) {
					indexList();
				}
			}
			Node value = accept(":=") ? expr() : null;
			expect(";");
			if (pkg.vars.isEmpty()) {
				if (name.equals("east_version") && value instanceof Str s) {
					pkg.version = s.value();
				}
				pkg.constants.put(name, new Const(type, value, line));
			} else if ("eof".equals(type)) {
				if (pkg.untilEof) {
					throw new EastException(line, "The EOF marker can only be used once");
				}
				pkg.untilEof = true;
			} else {
				throw new EastException(line, "'" + raw + "' is a marker: the variable before it repeats until "
						+ "this value is found. Markers other than EOF aren't supported yet");
			}
			return;
		}
		if (pkg.untilEof) {
			throw new EastException(line, "The EOF marker must be the last declaration");
		}
		String type = ident().s();
		List<Index> constraint = is("(") ? indexList() : null;
		if (accept(":=")) {
			expr();
		}
		expect(";");
		pkg.vars.add(new Var(name, type, constraint, line));
	}

	// ------------------------------------------------------------------ expressions

	private Node expr() {
		Node left = relation();
		while (is("and") || is("or")) {
			Tok op = next();
			left = new Bin(op.s(), left, relation(), op.line());
		}
		return left;
	}

	private Node relation() {
		Node left = sum();
		if (is("=") || is("/=") || is("<") || is("<=") || is(">") || is(">=")) {
			Tok op = next();
			return new Bin(op.s(), left, sum(), op.line());
		}
		return left;
	}

	private Node sum() {
		Node left;
		if (is("-") || is("+")) {
			Tok op = next();
			left = new Un(op.s(), term(), op.line());
		} else {
			left = term();
		}
		while (is("+") || is("-") || is("&")) {
			Tok op = next();
			left = new Bin(op.s(), left, term(), op.line());
		}
		return left;
	}

	private Node term() {
		Node left = factor();
		while (is("*") || is("/") || is("mod") || is("rem")) {
			Tok op = next();
			left = new Bin(op.s(), left, factor(), op.line());
		}
		return left;
	}

	private Node factor() {
		if (is("not") || is("abs")) {
			Tok op = next();
			return new Un(op.s(), primary(), op.line());
		}
		Node base = primary();
		if (is("**")) {
			Tok op = next();
			return new Bin("**", base, primary(), op.line());
		}
		return base;
	}

	private Node primary() {
		Node p = atom();
		while (is("!")) {
			Tok op = next();
			p = new Call("!", List.of(p), op.line());
		}
		return p;
	}

	private Node atom() {
		Tok t = peek();
		switch (t.k()) {
			case NUM -> {
				next();
				return new Num(t.num(), t.real());
			}
			case STRING -> {
				next();
				return new Str(t.s());
			}
			case CHAR -> {
				next();
				return new Chr(t.s().charAt(0));
			}
			case IDENT -> {
				next();
				List<String> path = new ArrayList<>(List.of(t.s()));
				StringBuilder raw = new StringBuilder(t.raw());
				while (is(".") && peek(1).k() == K.IDENT) {
					next();
					Tok part = next();
					path.add(part.s());
					raw.append('.').append(part.raw());
				}
				if (path.size() == 1 && is("(")) {
					next();
					List<Node> args = new ArrayList<>();
					do {
						args.add(expr());
					} while (accept(","));
					expect(")");
					return new Call(t.s(), args, t.line());
				}
				return new Name(path, raw.toString(), t.line());
			}
			default -> {
				if (is("(")) {
					return aggregate();
				}
				throw error("Expected a value");
			}
		}
	}

	/** {@code ( item {, item} )}; a single positional item is just a parenthesised expression. */
	private Node aggregate() {
		expect("(");
		List<Agg.Item> items = new ArrayList<>();
		do {
			if (accept("others")) {
				expect("=>");
				items.add(new Agg.Item(List.of(), true, expr()));
				continue;
			}
			Node first = expr();
			if (is("|") || is("=>")) {
				List<Node> keys = new ArrayList<>(List.of(first));
				while (accept("|")) {
					keys.add(expr());
				}
				expect("=>");
				items.add(new Agg.Item(keys, false, expr()));
			} else {
				items.add(new Agg.Item(null, false, first));
			}
		} while (accept(","));
		expect(")");
		if (items.size() == 1 && items.get(0).keys() == null && !items.get(0).others()) {
			return items.get(0).value();
		}
		return new Agg(items);
	}

	/** The value of a static expression of literals and number constants; null if it isn't one. */
	private static BigDecimal staticValue(Pkg pkg, Node n) {
		return staticValue(pkg, n, 0);
	}

	private static BigDecimal staticValue(Pkg pkg, Node n, int depth) {
		if (depth > 50) {
			return null;
		}
		if (n instanceof Num num) {
			return num.value();
		}
		if (n instanceof Name name && name.path().size() == 1) {
			switch (name.last()) {
				case "word_32_bits":
					return BigDecimal.valueOf(32);
				case "word_16_bits":
					return BigDecimal.valueOf(16);
				default:
					Const c = pkg.constants.get(name.last());
					return c == null || c.value() == null ? null : staticValue(pkg, c.value(), depth + 1);
			}
		}
		if (n instanceof Un u) {
			BigDecimal v = staticValue(pkg, u.operand(), depth + 1);
			if (v == null) {
				return null;
			}
			return switch (u.op()) {
				case "-" -> v.negate();
				case "+" -> v;
				case "abs" -> v.abs();
				default -> null;
			};
		}
		if (n instanceof Bin b) {
			BigDecimal l = staticValue(pkg, b.left(), depth + 1);
			BigDecimal r = staticValue(pkg, b.right(), depth + 1);
			if (l == null || r == null) {
				return null;
			}
			return switch (b.op()) {
				case "+" -> l.add(r);
				case "-" -> l.subtract(r);
				case "*" -> l.multiply(r);
				case "/" -> r.signum() == 0 ? null
						: isWhole(l) && isWhole(r) ? new BigDecimal(l.toBigInteger().divide(r.toBigInteger()))
								: l.divide(r, 20, RoundingMode.HALF_EVEN);
				case "**" -> isWhole(r) && r.signum() >= 0 && r.intValue() < 1000 ? l.pow(r.intValue()) : null;
				default -> null;
			};
		}
		return null;
	}

	private static boolean isWhole(BigDecimal v) {
		return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
	}

	// ------------------------------------------------------------------ building the description

	/** How the physical package says a scalar type is represented. */
	private record Rep(String kind, Agg value, int line) {
	}

	/** What one element became, and how many bits it takes when that's known. */
	private record Built(ElementDescription element, Long bits, boolean byteMultiple) {
	}

	/**
	 * Where an element is being built: its EAST path, those of the record
	 * instances enclosing it, and the values the enclosing record's
	 * discriminants stand for.
	 */
	private record Ctx(String path, List<String> ancestors, Map<String, Expression> bindings) {
		Ctx child(String name) {
			return new Ctx(path == null ? name : path + "." + name, ancestors, bindings);
		}
	}

	private static final class Builder {
		private final Pkg logical;
		private final Pkg physical;
		private ByteOrder order = ByteOrder.BIG_ENDIAN;
		private boolean lastIndexFirst;
		private final Map<String, Rep> reps = new HashMap<>();
		private final List<String> asText = new ArrayList<>();

		Builder(Pkg logical, Pkg physical) {
			this.logical = logical;
			this.physical = physical;
			EnumTy character = new EnumTy("character", 0, List.of(), List.of());
			character.size = 8L;
			EnumTy bool = new EnumTy("boolean", 0, List.of("false", "true"), List.of("FALSE", "TRUE"));
			logical.types.putIfAbsent("character", character);
			logical.types.putIfAbsent("boolean", bool);
			logical.types.putIfAbsent("string", new ArrTy("string", 0,
					List.of(new Index("positive", null, null, true)), "character"));
		}

		FormatDescription build() {
			readPhysical();
			if (logical.vars.isEmpty()) {
				throw new EastException(0, "The logical package '" + logical.raw + "' declares no variables, "
						+ "so it doesn't describe any data");
			}
			List<ElementDescription> children = new ArrayList<>();
			Integer phase = 0;
			for (int i = 0; i < logical.vars.size(); i++) {
				Var v = logical.vars.get(i);
				Ctx ctx = new Ctx(v.name(), List.of(), Map.of());
				Built b = object(v.name(), v.type(), v.constraint(), ctx, phase, v.line());
				ElementDescription e = b.element();
				if (logical.untilEof && i == logical.vars.size() - 1) {
					e = repeat(e, new Occurrence.UntilEnd());
				}
				children.add(e);
				phase = advance(phase, b);
			}
			RecordDescription root;
			String rootName = logical.name;
			if (logical.untilEof) {
				root = new RecordDescription(ElementDescription.newId(), rootName, children, null, Occurrence.ONCE,
						Semantics.NONE);
			} else {
				RecordDescription set = new RecordDescription(ElementDescription.newId(), "set", children, null,
						new Occurrence.UntilEnd(), Semantics.of(null,
								"One set of the data the EAST description describes; the description is applied "
										+ "repeatedly to the whole of the data.", null));
				root = new RecordDescription(ElementDescription.newId(), rootName, List.of(set), null,
						Occurrence.ONCE, Semantics.NONE);
			}
			StringBuilder notes = new StringBuilder("Read from an EAST Data Description Record (CCSDS 644.0-B-3): "
					+ "logical package " + logical.raw);
			if (physical != null) {
				notes.append(", physical package ").append(physical.raw);
			}
			notes.append(logical.version != null ? "; EAST version " + logical.version + "." : ".");
			if (!asText.isEmpty()) {
				notes.append(" Numbers in an ASCII representation are read as text: ")
						.append(String.join(", ", asText)).append('.');
			}
			return new FormatDescription(logical.raw, notes.toString(), order, List.of(), root);
		}

		// -------------------------------------------------- physical package

		private void readPhysical() {
			if (physical == null) {
				return;
			}
			Const octets = physical.constants.get("octet_storage");
			if (octets != null && octets.value() instanceof Name n) {
				if (n.joined().equals("low_order_first")) {
					order = ByteOrder.LITTLE_ENDIAN;
				} else if (!n.joined().equals("high_order_first")) {
					throw new EastException(octets.line(), "OCTET_STORAGE must be HIGH_ORDER_FIRST or LOW_ORDER_FIRST");
				}
			}
			Const arrays = physical.constants.get("array_storage");
			if (arrays != null && arrays.value() instanceof Name n) {
				lastIndexFirst = n.joined().equals("last_index_first");
			}
			if (physical.types.get("relation") instanceof RecTy relation && relation.variant != null) {
				for (When w : relation.variant.whens()) {
					for (Choice c : w.choices()) {
						if (!(c.value() instanceof Name n) || !n.joined().startsWith("user_type_")) {
							continue;
						}
						String userType = n.joined().substring("user_type_".length());
						for (Comp comp : w.comps()) {
							if (comp.value() instanceof Name constName) {
								Const rep = physical.constants.get(constName.joined());
								if (rep == null || !(rep.value() instanceof Agg agg)) {
									throw new EastException(comp.line(), "'" + constName.raw()
											+ "' isn't declared in the physical package with a value");
								}
								reps.put(userType, new Rep(comp.type(), agg, rep.line()));
							}
						}
					}
				}
			}
		}

		/** The representation the physical package gives a type, or one it's a subtype of. */
		private Rep repFor(String typeName) {
			String name = typeName;
			for (int i = 0; i < 50 && name != null; i++) {
				Rep rep = reps.get(name);
				if (rep != null) {
					return rep;
				}
				name = logical.types.get(name) instanceof SubTy s ? s.base : null;
			}
			return null;
		}

		// -------------------------------------------------- types

		private Ty type(String name, int line) {
			Ty t = logical.types.get(name);
			if (t == null) {
				throw new EastException(line, "The type '" + name.toUpperCase(Locale.ROOT) + "' isn't declared");
			}
			return t;
		}

		/** A type with its subtypes' constraints applied. */
		private record View(Ty base, List<Index> constraint, Node lo, Node hi) {
		}

		private View view(String name, int line) {
			Ty t = type(name, line);
			List<Index> constraint = null;
			Node lo = null;
			Node hi = null;
			for (int i = 0; t instanceof SubTy s; i++) {
				if (i > 50) {
					throw new EastException(line, "The subtypes of '" + name + "' go round in a circle");
				}
				if (constraint == null) {
					constraint = s.constraint;
				}
				if (lo == null) {
					lo = s.lo;
					hi = s.hi;
				}
				t = type(s.base, s.line);
			}
			return new View(t, constraint, lo, hi);
		}

		private boolean isCharacter(String name, int line) {
			return view(name, line).base().name.equals("character");
		}

		private Long size(Ty t, int line) {
			if (t.size == null) {
				throw new EastException(t.line > 0 ? t.line : line, "'" + t.name.toUpperCase(Locale.ROOT)
						+ "' needs a size: for " + t.name.toUpperCase(Locale.ROOT) + "'size use <bits>;");
			}
			return t.size;
		}

		// -------------------------------------------------- elements

		private Built object(String name, String typeName, List<Index> constraint, Ctx ctx, Integer phase, int line) {
			View v = view(typeName, line);
			Ty t = v.base();
			if (t instanceof RecTy r) {
				if (constraint != null) {
					throw new EastException(line, "A record can't be given an index constraint");
				}
				return record(name, r, ctx, phase, line);
			}
			if (t instanceof ArrTy a) {
				List<Index> indices = constraint != null ? constraint : v.constraint() != null ? v.constraint()
						: a.indices;
				if (indices.stream().anyMatch(Index::unconstrained)) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' needs the number of elements "
							+ "of " + typeName.toUpperCase(Locale.ROOT) + ", e.g. (1 .. 10)");
				}
				if (indices.size() != a.indices.size()) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' gives "
							+ indices.size() + " index ranges for an array with " + a.indices.size());
				}
				return array(name, a, indices, ctx, phase, line);
			}
			if (constraint != null) {
				throw new EastException(line, "Only an array can be given an index constraint");
			}
			return scalar(name, typeName, v, phase, line);
		}

		private Built scalar(String name, String typeName, View v, Integer phase, int line) {
			Ty t = v.base();
			Rep rep = repFor(typeName);
			if (t instanceof EnumTy e) {
				if (e.name.equals("character")) {
					return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(1), Semantics.NONE),
							8L, true);
				}
				long bits = size(e, line);
				Map<String, String> codes = new LinkedHashMap<>();
				if (rep != null) {
					if (!rep.kind().equals("ascii_enumeration_physical_description")) {
						throw new EastException(rep.line(), "The representation of the enumeration '"
								+ typeName.toUpperCase(Locale.ROOT) + "' must be an ASCII_ENUMERATION_PHYSICAL_DESCRIPTION");
					}
					long chars = number(rep, "number_of_characters", 1);
					checkChars(typeName, bits, chars, rep.line());
					Node strings = rep.value().get("representation", 2);
					if (!(strings instanceof Agg list) || list.items().size() != e.literals.size()) {
						throw new EastException(rep.line(), "The ASCII representation of '"
								+ typeName.toUpperCase(Locale.ROOT) + "' needs one string for each literal");
					}
					for (int i = 0; i < e.literals.size(); i++) {
						if (!(list.items().get(i).value() instanceof Str s)) {
							throw new EastException(rep.line(), "The ASCII representation of '"
									+ typeName.toUpperCase(Locale.ROOT) + "' must be strings");
						}
						codes.put(pad(s.value(), (int) chars), e.raw.get(i));
					}
					return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(chars),
							new Semantics(null, null, null, null, null, codes, null, null, null, null, null)),
							bits, bits % 8 == 0);
				}
				boolean signed = false;
				for (int i = 0; i < e.literals.size(); i++) {
					BigInteger code = e.code(e.literals.get(i));
					signed |= code.signum() < 0;
					codes.put(code.toString(), e.raw.get(i));
				}
				return new Built(integer(name, typeName, bits, signed, phase, line,
						new Semantics(null, null, null, null, null, codes, null, null, null, null, null)), bits,
						bits % 8 == 0);
			}
			if (t instanceof IntTy i) {
				long bits = size(i, line);
				BigDecimal lo = staticValue(logical, v.lo() != null ? v.lo() : i.lo);
				BigDecimal hi = staticValue(logical, v.hi() != null ? v.hi() : i.hi);
				if (lo == null || hi == null) {
					throw new EastException(i.line, "The range of '" + i.name.toUpperCase(Locale.ROOT)
							+ "' must be numbers or number constants");
				}
				Semantics range = new Semantics(null, null, null, null, null, Map.of(), null, null, null, lo, hi);
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(name, typeName, "integer", bits, rep, range);
				}
				if (rep != null) {
					checkIntegerRep(typeName, rep, bits, lo.signum() < 0);
				}
				return new Built(integer(name, typeName, bits, lo.signum() < 0, phase, line, range), bits,
						bits % 8 == 0);
			}
			if (t instanceof RealTy r) {
				long bits = size(r, line);
				Node loNode = v.lo() != null ? v.lo() : r.lo;
				Node hiNode = v.hi() != null ? v.hi() : r.hi;
				BigDecimal lo = loNode == null ? null : staticValue(logical, loNode);
				BigDecimal hi = hiNode == null ? null : staticValue(logical, hiNode);
				Semantics range = new Semantics(null, null, null, null, null, Map.of(), null, null, null, lo, hi);
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(name, typeName, "real", bits, rep, range);
				}
				if (rep != null) {
					Node convention = rep.value().get("convention_used", 2);
					if (!(convention instanceof Name c) || !c.joined().equals("fcstc000")) {
						throw new EastException(rep.line(), "The real type '" + typeName.toUpperCase(Locale.ROOT)
								+ "' isn't in IEEE 754 (FCSTC000); other conventions aren't supported yet");
					}
				}
				if (phase != null && phase != 0) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT)
							+ "' is a real that doesn't start on a byte boundary, which isn't supported");
				}
				PrimitiveType type = bits == 32 ? PrimitiveType.FLOAT32 : bits == 64 ? PrimitiveType.FLOAT64 : null;
				if (type == null) {
					throw new EastException(r.line, "'" + r.name.toUpperCase(Locale.ROOT) + "' is a " + bits
							+ "-bit real; only 32- and 64-bit IEEE 754 reals are supported");
				}
				return new Built(field(name, type, null, range), bits, true);
			}
			throw new EastException(line, "'" + typeName.toUpperCase(Locale.ROOT) + "' can't be used for data");
		}

		private Built asciiNumber(String name, String typeName, String kind, long bits, Rep rep, Semantics range) {
			long chars = number(rep, "number_of_characters", 0);
			checkChars(typeName, bits, chars, rep.line());
			if (!asText.contains(typeName.toUpperCase(Locale.ROOT))) {
				asText.add(typeName.toUpperCase(Locale.ROOT));
			}
			Semantics s = new Semantics(null, "An EAST ASCII-encoded decimal " + kind + " in " + chars
					+ " characters.", null, null, null, Map.of(), null, null, null, range.validMin(), range.validMax());
			return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(chars), s), bits, true);
		}

		private static void checkChars(String typeName, long bits, long chars, int line) {
			if (chars * 8 != bits) {
				throw new EastException(line, "'" + typeName.toUpperCase(Locale.ROOT) + "' is " + bits
						+ " bits but its ASCII representation is " + chars + " characters");
			}
		}

		private static String pad(String s, int width) {
			StringBuilder sb = new StringBuilder(s);
			while (sb.length() < width) {
				sb.append(' ');
			}
			return sb.toString();
		}

		private long number(Rep rep, String component, int index) {
			Node n = rep.value().get(component, index);
			BigDecimal v = n == null ? null : staticValue(physical, n);
			if (v == null) {
				throw new EastException(rep.line(), "The representation needs " + component.toUpperCase(Locale.ROOT));
			}
			return v.longValue();
		}

		/** Accepts the binary integer representations every engine reads: one run of bits, two's complement or unsigned. */
		private void checkIntegerRep(String typeName, Rep rep, long bits, boolean negative) {
			String what = "'" + typeName.toUpperCase(Locale.ROOT) + "'";
			if (!rep.kind().equals("integer_physical_description")) {
				throw new EastException(rep.line(), "The representation of the integer " + what
						+ " must be an INTEGER_PHYSICAL_DESCRIPTION or ASCII_NUMERIC_PHYSICAL_DESCRIPTION");
			}
			Node complement = rep.value().get("complement", 1);
			String c = complement instanceof Name n ? n.joined() : "";
			if (!c.equals("twos_complement") && !c.equals("unsigned")) {
				throw new EastException(rep.line(), what + " is in " + c.toUpperCase(Locale.ROOT)
						+ "; only two's complement and unsigned integers are supported yet");
			}
			if (c.equals("unsigned") && negative) {
				throw new EastException(rep.line(), what + " has negative values, so it can't be UNSIGNED");
			}
			Node location = rep.value().get("location", 2);
			if (location instanceof Agg subfields) {
				if (subfields.items().size() != 1) {
					throw new EastException(rep.line(), "The bits of " + what
							+ " are in several pieces, which isn't supported yet");
				}
				Node only = subfields.items().get(0).value();
				if (only instanceof Agg range) {
					BigDecimal first = staticValue(physical, range.get("beginning_at_bit_number", 0));
					BigDecimal last = staticValue(physical, range.get("ending_at_bit_number", 1));
					if (first == null || last == null || first.longValue() != 0
							|| last.longValue() != bits - 1) {
						throw new EastException(rep.line(), "The bits of " + what + " must run from bit 0 to bit "
								+ (bits - 1) + "; other orders aren't supported yet");
					}
				}
			}
		}

		private FieldDescription integer(String name, String typeName, long bits, boolean signed, Integer phase,
				int line, Semantics semantics) {
			boolean aligned = phase == null || phase == 0;
			if (aligned && (bits == 8 || bits == 16 || bits == 32 || bits == 64)) {
				PrimitiveType type = switch ((int) bits) {
					case 8 -> signed ? PrimitiveType.INT8 : PrimitiveType.UINT8;
					case 16 -> signed ? PrimitiveType.INT16 : PrimitiveType.UINT16;
					case 32 -> signed ? PrimitiveType.INT32 : PrimitiveType.UINT32;
					default -> signed ? PrimitiveType.INT64 : PrimitiveType.UINT64;
				};
				return field(name, type, null, semantics);
			}
			if (signed) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' (" + typeName.toUpperCase(Locale.ROOT)
						+ ") is a signed " + bits + "-bit field; signed bit fields aren't supported yet");
			}
			if (bits > 64) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' is " + bits
						+ " bits; integers of more than 64 bits aren't supported");
			}
			if (order == ByteOrder.LITTLE_ENDIAN) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' is a " + bits
						+ "-bit field in LOW_ORDER_FIRST data; bit fields stored least significant bit first "
						+ "aren't supported yet");
			}
			return field(name, PrimitiveType.BITS, new Expression.IntLiteral(bits), semantics);
		}

		private static FieldDescription field(String name, PrimitiveType type, Expression length, Semantics semantics) {
			return new FieldDescription(ElementDescription.newId(), name, type, length, null, Occurrence.ONCE,
					semantics);
		}

		// -------------------------------------------------- arrays

		private Built array(String name, ArrTy a, List<Index> indices, Ctx ctx, Integer phase, int line) {
			List<Expression> counts = new ArrayList<>();
			for (Index index : indices) {
				counts.add(count(index, ctx, line));
			}
			// The index that varies fastest is innermost.
			List<Expression> fastestFirst = new ArrayList<>(counts);
			if (lastIndexFirst) {
				Collections.reverse(fastestFirst);
			}
			Long total = 1L;
			for (Expression c : counts) {
				total = c instanceof Expression.IntLiteral lit && total != null ? total * lit.value() : null;
			}
			int levels = fastestFirst.size();
			Built inner;
			int start;
			if (isCharacter(a.element, line)) {
				String innerName = levels == 1 ? name : "element";
				inner = new Built(field(innerName, PrimitiveType.STRING, fastestFirst.get(0), Semantics.NONE),
						fastestFirst.get(0) instanceof Expression.IntLiteral lit ? lit.value() * 8 : null, true);
				start = 1;
			} else {
				String innerName = levels == 1 ? name : "element";
				Long elementBits = staticSize(a.element, line);
				Integer elementPhase = elementBits != null && elementBits % 8 != 0 ? Integer.valueOf(1) : phase;
				Built element = object(innerName, a.element, null, ctx, elementPhase, line);
				if (elementBits != null && elementBits % 8 != 0 && phase != null && phase != 0) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT)
							+ "' is an array of bit fields that doesn't start on a byte boundary");
				}
				Expression n = fastestFirst.get(0);
				inner = new Built(repeat(element.element(), new Occurrence.Repeated(n)),
						element.bits() != null && n instanceof Expression.IntLiteral lit ? element.bits() * lit.value()
								: null, element.byteMultiple());
				start = 1;
			}
			for (int level = start; level < levels; level++) {
				String levelName = level == levels - 1 ? name : "dim_" + (level + 1);
				Expression n = fastestFirst.get(level);
				RecordDescription wrapper = new RecordDescription(ElementDescription.newId(), levelName,
						List.of(inner.element()), null, new Occurrence.Repeated(n), Semantics.NONE);
				inner = new Built(wrapper, inner.bits() != null && n instanceof Expression.IntLiteral lit
						? inner.bits() * lit.value() : null, inner.byteMultiple());
			}
			if (a.size != null && inner.bits() != null && !a.size.equals(inner.bits())) {
				throw new EastException(a.line, "'" + a.name.toUpperCase(Locale.ROOT) + "' is given a size of "
						+ a.size + " bits, but its elements take " + inner.bits());
			}
			return inner;
		}

		/** The number of values an index range covers, as an expression. */
		private Expression count(Index index, Ctx ctx, int line) {
			if (index.lo() == null) {
				Ty t = view(index.typeName(), line).base();
				if (t instanceof EnumTy e) {
					return new Expression.IntLiteral(e.literals.size());
				}
				if (t instanceof IntTy i) {
					BigDecimal lo = staticValue(logical, i.lo);
					BigDecimal hi = staticValue(logical, i.hi);
					if (lo != null && hi != null) {
						return new Expression.IntLiteral(Math.max(0, hi.longValue() - lo.longValue() + 1));
					}
				}
				throw new EastException(line, "The index type '" + index.typeName().toUpperCase(Locale.ROOT)
						+ "' must be an enumeration or an integer range of numbers");
			}
			Integer loOrdinal = enumOrdinal(index.lo());
			Integer hiOrdinal = enumOrdinal(index.hi());
			if (loOrdinal != null && hiOrdinal != null) {
				return new Expression.IntLiteral(Math.max(0, hiOrdinal - loOrdinal + 1));
			}
			BigDecimal lo = staticValue(logical, index.lo());
			BigDecimal hi = staticValue(logical, index.hi());
			if (lo != null && hi != null) {
				return new Expression.IntLiteral(Math.max(0, hi.longValue() - lo.longValue() + 1));
			}
			Expression high = expression(index.hi(), ctx, line);
			if (lo != null && lo.longValue() == 1) {
				return high;
			}
			Expression low = lo != null ? new Expression.IntLiteral(lo.longValue()) : expression(index.lo(), ctx, line);
			return new Expression.Binary(Expression.Op.ADD, new Expression.Binary(Expression.Op.SUB, high, low),
					new Expression.IntLiteral(1));
		}

		private Integer enumOrdinal(Node n) {
			if (n instanceof Name name && name.path().size() == 1) {
				for (Ty t : logical.types.values()) {
					if (t instanceof EnumTy e && e.literals.contains(name.last())) {
						return e.literals.indexOf(name.last());
					}
				}
			}
			if (n instanceof Chr c) {
				return (int) c.value();
			}
			return null;
		}

		/** The size of a type when it doesn't depend on the data; null otherwise. */
		private Long staticSize(String typeName, int line) {
			View v = view(typeName, line);
			Ty t = v.base();
			if (t.size != null) {
				return t.size;
			}
			if (t instanceof ArrTy a) {
				List<Index> indices = v.constraint() != null ? v.constraint() : a.indices;
				Long element = staticSize(a.element, line);
				if (element == null) {
					return null;
				}
				long total = element;
				for (Index index : indices) {
					if (index.unconstrained()) {
						return null;
					}
					Expression c;
					try {
						c = count(index, new Ctx(null, List.of(), Map.of()), line);
					} catch (EastException e) {
						return null;
					}
					if (!(c instanceof Expression.IntLiteral lit)) {
						return null;
					}
					total *= lit.value();
				}
				return total;
			}
			return null;
		}

		// -------------------------------------------------- records

		/** One component to place in a record: its name, type, and position from the representation clause. */
		private record Part(String name, String type, List<Index> constraint, long[] position, int line) {
		}

		private Built record(String name, RecTy r, Ctx outer, Integer phase, int line) {
			List<String> ancestors = new ArrayList<>(outer.ancestors());
			ancestors.add(outer.path());
			Map<String, Expression> bindings = new HashMap<>();
			Ctx ctx = new Ctx(outer.path(), ancestors, bindings);
			List<Part> fixed = new ArrayList<>();
			for (Discr d : r.discriminants) {
				if (d.virtual()) {
					String path = outer.path() + "." + d.name();
					Node actual = logical.actuals.get(path);
					if (actual == null) {
						throw new EastException(d.line(), "No actual value is given for the virtual discriminant "
								+ path.toUpperCase(Locale.ROOT) + " (" + path.toUpperCase(Locale.ROOT)
								+ " : virtual <type> := <value>;)");
					}
					bindings.put(d.name(), expression(actual, ctx, logical.actualLines.get(path)));
				} else {
					bindings.put(d.name(), new Expression.FieldRef(d.name()));
					fixed.add(new Part(d.name(), d.type(), null, r.layout.get(d.name()), d.line()));
				}
			}
			for (Comp c : r.comps) {
				if (c.constant()) {
					throw new EastException(c.line(), "'" + c.name().toUpperCase(Locale.ROOT) + "' is a marker: the "
							+ "component before it repeats until this value is found. Markers aren't supported yet");
				}
				fixed.add(new Part(c.name(), c.type(), c.constraint(), r.layout.get(c.name()), c.line()));
			}
			Body body = body(fixed, r.variant, r, 0, r.size, ctx, phase);
			RecordDescription record = new RecordDescription(ElementDescription.newId(), name, body.children(), null,
					Occurrence.ONCE, Semantics.NONE);
			return new Built(record, body.bits(), body.byteMultiple());
		}

		private record Body(List<ElementDescription> children, Long bits, boolean byteMultiple) {
		}

		/**
		 * Lays out a record's components, or one alternative of its variant
		 * part: those with a position in order of position, with the unused
		 * space between them, then the rest in the order declared.
		 *
		 * @param base  where this body starts, in bits from the start of the record
		 * @param padTo how many bits the body must take, if known: unused space is added at its end
		 */
		private Body body(List<Part> parts, Variant variant, RecTy r, long base, Long padTo, Ctx ctx,
				Integer phase) {
			List<ElementDescription> out = new ArrayList<>();
			int[] spares = {0};
			long[] variantSpan = null;
			if (variant != null) {
				List<String> names = new ArrayList<>();
				variant.names(names);
				for (String n : names) {
					long[] p = r.layout.get(n);
					if (p != null) {
						variantSpan = variantSpan == null ? new long[] {p[0], p[1]}
								: new long[] {Math.min(variantSpan[0], p[0]), Math.max(variantSpan[1], p[1])};
					}
				}
			}
			List<Object> placed = new ArrayList<>();
			List<Part> rest = new ArrayList<>();
			for (Part p : parts) {
				if (p.position() != null) {
					placed.add(p);
				} else {
					rest.add(p);
				}
			}
			if (variantSpan != null) {
				placed.add(variantSpan);
			}
			placed.sort((x, y) -> Long.compare(start(x), start(y)));
			long cursor = 0;
			boolean known = true;
			boolean byteMultiple = true;
			Integer ph = phase;
			for (Object item : placed) {
				long at = start(item) - base;
				if (at < cursor) {
					throw new EastException(lineOf(item, variant), "'" + nameOf(item) + "' overlaps the component "
							+ "before it in the representation clause of '" + r.name.toUpperCase(Locale.ROOT) + "'");
				}
				if (at > cursor) {
					ph = spare(out, spares, at - cursor, ph);
					cursor = at;
				}
				long width = end(item) - start(item) + 1;
				if (item instanceof Part p) {
					Built b = object(p.name(), p.type(), p.constraint(), ctx.child(p.name()), ph, p.line());
					if (b.bits() != null && b.bits() != width) {
						throw new EastException(p.line(), "'" + p.name().toUpperCase(Locale.ROOT) + "' takes "
								+ b.bits() + " bits, but its representation clause gives it " + width);
					}
					out.add(b.element());
				} else {
					variantElements(variant, r, start(item), width, ctx, ph, out);
				}
				cursor += width;
				ph = ph == null ? null : (int) ((ph + width) % 8);
			}
			for (Part p : rest) {
				Built b = object(p.name(), p.type(), p.constraint(), ctx.child(p.name()), ph, p.line());
				out.add(b.element());
				if (b.bits() != null && known) {
					cursor += b.bits();
				} else {
					known = false;
				}
				byteMultiple &= b.byteMultiple();
				ph = advance(ph, b);
			}
			if (variant != null && variantSpan == null) {
				Built v = variantElements(variant, r, -1, null, ctx, ph, out);
				if (v.bits() != null && known) {
					cursor += v.bits();
				} else {
					known = false;
				}
				byteMultiple &= v.byteMultiple();
				ph = advance(ph, v);
			}
			if (padTo != null && known) {
				if (cursor > padTo) {
					throw new EastException(r.line, "The components of '" + r.name.toUpperCase(Locale.ROOT)
							+ "' take " + cursor + " bits, more than its size of " + padTo);
				}
				if (cursor < padTo) {
					ph = spare(out, spares, padTo - cursor, ph);
					cursor = padTo;
				}
			}
			return new Body(out, known ? Long.valueOf(cursor) : null, known ? cursor % 8 == 0 : byteMultiple);
		}

		private static long start(Object item) {
			return item instanceof Part p ? p.position()[0] : ((long[]) item)[0];
		}

		private static long end(Object item) {
			return item instanceof Part p ? p.position()[1] : ((long[]) item)[1];
		}

		private static String nameOf(Object item) {
			return item instanceof Part p ? p.name().toUpperCase(Locale.ROOT) : "the variant part";
		}

		private static int lineOf(Object item, Variant variant) {
			return item instanceof Part p ? p.line() : variant.line();
		}

		/** Adds unused space as {@code spare_n} fields: bytes when on a byte boundary, else bit fields. */
		private Integer spare(List<ElementDescription> out, int[] spares, long bits, Integer phase) {
			long left = bits;
			Integer ph = phase;
			while (left > 0) {
				String name = "spare_" + (++spares[0]);
				Semantics unused = Semantics.of(null, "Unused space.", null);
				if ((ph == null || ph == 0) && left % 8 == 0) {
					out.add(field(name, PrimitiveType.BYTES, new Expression.IntLiteral(left / 8), unused));
					left = 0;
				} else {
					long chunk = ph != null && ph != 0 ? Math.min(left, 8 - ph) : Math.min(left, left % 8 == 0 ? 8 : left % 8);
					if (order == ByteOrder.LITTLE_ENDIAN) {
						throw new EastException(0, "Unused space of " + bits + " bits in LOW_ORDER_FIRST data isn't "
								+ "supported yet");
					}
					out.add(field(name, PrimitiveType.BITS, new Expression.IntLiteral(chunk), unused));
					left -= chunk;
					ph = ph == null ? null : (int) ((ph + chunk) % 8);
				}
			}
			return ph;
		}

		/**
		 * The variant part: a choice when each alternative is chosen by one
		 * value, otherwise an optional record for each alternative.
		 *
		 * @param at    where it starts, in bits from the start of the record; -1 if it isn't positioned
		 * @param width how many bits it takes, if the representation clause says
		 */
		private Built variantElements(Variant v, RecTy r, long at, Long width, Ctx ctx, Integer phase,
				List<ElementDescription> out) {
			Expression discriminant = ctx.bindings().get(v.discriminant());
			if (discriminant == null) {
				throw new EastException(v.line(), "'" + v.discriminant().toUpperCase(Locale.ROOT)
						+ "' isn't a discriminant of '" + r.name.toUpperCase(Locale.ROOT) + "'");
			}
			Discr d = r.discriminants.stream().filter(x -> x.name().equals(v.discriminant())).findFirst().orElseThrow();
			boolean simple = !discriminant.isBoolean() && v.whens().stream().allMatch(w -> w.choices().size() == 1
					&& !w.choices().get(0).others() && w.choices().get(0).hi() == null
					&& (!w.comps().isEmpty() || w.nested() != null));
			List<Body> bodies = new ArrayList<>();
			for (When w : v.whens()) {
				List<Part> parts = new ArrayList<>();
				for (Comp c : w.comps()) {
					if (c.constant()) {
						throw new EastException(c.line(), "Markers aren't supported yet");
					}
					parts.add(new Part(c.name(), c.type(), c.constraint(), r.layout.get(c.name()), c.line()));
				}
				bodies.add(body(parts, w.nested(), r, Math.max(at, 0), width, ctx, phase));
			}
			Long bits = width;
			if (bits == null && !bodies.isEmpty() && bodies.stream().allMatch(b -> b.bits() != null)
					&& bodies.stream().map(Body::bits).distinct().count() == 1) {
				bits = bodies.get(0).bits();
			}
			boolean byteMultiple = bodies.stream().allMatch(Body::byteMultiple);
			if (simple) {
				List<ChoiceDescription.Branch> branches = new ArrayList<>();
				for (int i = 0; i < v.whens().size(); i++) {
					When w = v.whens().get(i);
					BigInteger key = choiceValue(w.choices().get(0).value(), d, w.line());
					branches.add(new ChoiceDescription.Branch(key.toString(), new RecordDescription(
							ElementDescription.newId(), alternativeName(w, d), bodies.get(i).children(), null,
							Occurrence.ONCE, Semantics.NONE)));
				}
				out.add(new ChoiceDescription(ElementDescription.newId(),
						"case_" + v.discriminant().replaceFirst("^virtual_", ""), discriminant,
						branches, Occurrence.ONCE, Semantics.NONE));
				return new Built(out.get(out.size() - 1), bits, byteMultiple);
			}
			Expression others = null;
			for (When w : v.whens()) {
				for (Choice c : w.choices()) {
					if (!c.others()) {
						Expression test = test(discriminant, c, d, w.line());
						others = others == null ? test : new Expression.Binary(Expression.Op.OR, others, test);
					}
				}
			}
			ElementDescription last = null;
			for (int i = 0; i < v.whens().size(); i++) {
				When w = v.whens().get(i);
				if (bodies.get(i).children().isEmpty()) {
					continue;
				}
				Expression condition = null;
				for (Choice c : w.choices()) {
					Expression test = c.others()
							? others == null ? null : new Expression.Unary(Expression.Op.NOT, others)
							: test(discriminant, c, d, w.line());
					if (test == null) {
						condition = null;
						break;
					}
					condition = condition == null ? test : new Expression.Binary(Expression.Op.OR, condition, test);
				}
				Occurrence occurrence = condition == null ? Occurrence.ONCE : new Occurrence.Optional(condition);
				last = new RecordDescription(ElementDescription.newId(), alternativeName(w, d),
						bodies.get(i).children(), null, occurrence, Semantics.NONE);
				out.add(last);
			}
			if (width == null && v.whens().stream().anyMatch(w -> w.comps().isEmpty() && w.nested() == null)) {
				bits = bodies.stream().allMatch(b -> b.bits() != null && b.bits() == 0) ? Long.valueOf(0) : null;
			}
			return new Built(last, bits, byteMultiple);
		}

		/** The test that the discriminant has a value, or one in a range. */
		private Expression test(Expression discriminant, Choice c, Discr d, int line) {
			if (discriminant.isBoolean()) {
				BigInteger v = choiceValue(c.value(), d, line);
				return v.signum() != 0 ? discriminant : new Expression.Unary(Expression.Op.NOT, discriminant);
			}
			Expression lo = new Expression.IntLiteral(choiceValue(c.value(), d, line).longValueExact());
			if (c.hi() == null) {
				return new Expression.Binary(Expression.Op.EQ, discriminant, lo);
			}
			Expression hi = new Expression.IntLiteral(choiceValue(c.hi(), d, line).longValueExact());
			return new Expression.Binary(Expression.Op.AND, new Expression.Binary(Expression.Op.GE, discriminant, lo),
					new Expression.Binary(Expression.Op.LE, discriminant, hi));
		}

		/** The value a choice names: an enumeration literal's code, a number, or a character's code. */
		private BigInteger choiceValue(Node n, Discr d, int line) {
			Ty t = view(d.type(), d.line()).base();
			if (n instanceof Name name && t instanceof EnumTy e) {
				BigInteger code = e.code(name.joined());
				if (code == null) {
					throw new EastException(line, "'" + name.raw() + "' isn't a value of "
							+ d.type().toUpperCase(Locale.ROOT));
				}
				return code;
			}
			if (n instanceof Chr c) {
				return BigInteger.valueOf(c.value());
			}
			BigDecimal v = staticValue(logical, n);
			if (v == null || !isWhole(v)) {
				throw new EastException(line, "A variant must be chosen by values of "
						+ d.type().toUpperCase(Locale.ROOT));
			}
			return v.toBigIntegerExact();
		}

		private static String alternativeName(When w, Discr d) {
			List<String> parts = new ArrayList<>();
			for (Choice c : w.choices()) {
				if (c.others()) {
					parts.add("others");
				} else {
					String lo = valueName(c.value());
					parts.add(c.hi() == null ? lo : lo + "_to_" + valueName(c.hi()));
				}
			}
			return "when_" + String.join("_or_", parts);
		}

		private static String valueName(Node n) {
			if (n instanceof Name name) {
				return name.last();
			}
			if (n instanceof Num num) {
				return num.value().toPlainString().replace("-", "minus_").replace('.', '_');
			}
			if (n instanceof Chr c) {
				return "char_" + (int) c.value();
			}
			return "value";
		}

		// -------------------------------------------------- expressions in the data

		/**
		 * An EAST expression as a description-model expression: discriminants
		 * become what they stand for, constants and enumeration literals their
		 * values, and EAST paths references to fields read earlier.
		 */
		private Expression expression(Node n, Ctx ctx, int line) {
			BigDecimal constant = staticValue(logical, n);
			if (constant != null) {
				if (!isWhole(constant)) {
					throw new EastException(line, "Only whole numbers can be used in the data's expressions, not "
							+ constant.toPlainString());
				}
				return new Expression.IntLiteral(constant.longValueExact());
			}
			if (n instanceof Name name) {
				if (name.path().size() == 1) {
					Expression bound = ctx.bindings().get(name.last());
					if (bound != null) {
						return bound;
					}
					for (Ty t : logical.types.values()) {
						if (t instanceof EnumTy e && e.literals.contains(name.last())) {
							return new Expression.IntLiteral(e.code(name.last()).longValueExact());
						}
					}
					return new Expression.FieldRef(name.last());
				}
				if (name.path().get(0).equals("ascii") && name.path().size() == 2) {
					Integer code = ASCII.get(name.last());
					if (code == null) {
						throw new EastException(line, "'" + name.raw() + "' isn't one of the ASCII constants");
					}
					return new Expression.IntLiteral(code);
				}
				String prefix = String.join(".", name.path().subList(0, name.path().size() - 1));
				if (ctx.ancestors().contains(prefix)) {
					return new Expression.FieldRef(name.last());
				}
				throw new EastException(line, "'" + name.raw() + "' is inside " + prefix.toUpperCase(Locale.ROOT)
						+ ", a record read earlier; references into an earlier record aren't supported yet");
			}
			if (n instanceof Chr c) {
				return new Expression.IntLiteral(c.value());
			}
			if (n instanceof Str s) {
				return new Expression.StringLiteral(s.value());
			}
			if (n instanceof Un u) {
				Expression operand = expression(u.operand(), ctx, line);
				return switch (u.op()) {
					case "-" -> new Expression.Unary(Expression.Op.NEG, operand);
					case "+" -> operand;
					case "not" -> new Expression.Unary(Expression.Op.NOT, operand);
					default -> throw new EastException(u.line(), "'" + u.op() + "' isn't supported in the data's "
							+ "expressions yet");
				};
			}
			if (n instanceof Bin b) {
				Expression.Op op = switch (b.op()) {
					case "+" -> Expression.Op.ADD;
					case "-" -> Expression.Op.SUB;
					case "*" -> Expression.Op.MUL;
					case "/" -> Expression.Op.DIV;
					case "mod", "rem" -> Expression.Op.MOD;
					case "=" -> Expression.Op.EQ;
					case "/=" -> Expression.Op.NE;
					case "<" -> Expression.Op.LT;
					case "<=" -> Expression.Op.LE;
					case ">" -> Expression.Op.GT;
					case ">=" -> Expression.Op.GE;
					case "and" -> Expression.Op.AND;
					case "or" -> Expression.Op.OR;
					default -> throw new EastException(b.line(), "'" + b.op() + "' on values from the data isn't "
							+ "supported yet");
				};
				return new Expression.Binary(op, expression(b.left(), ctx, line), expression(b.right(), ctx, line));
			}
			if (n instanceof Call call) {
				throw new EastException(call.line(), "The EAST operator '" + call.function()
						+ "' isn't supported in the data's expressions yet");
			}
			throw new EastException(line, "This value can't be used in the data's expressions");
		}

		private static Integer advance(Integer phase, Built b) {
			if (phase == null) {
				return null;
			}
			if (b.bits() != null) {
				return (int) ((phase + b.bits()) % 8);
			}
			return b.byteMultiple() ? phase : null;
		}

		private static ElementDescription repeat(ElementDescription e, Occurrence occurrence) {
			if (!(e.occurrence() instanceof Occurrence.Once)) {
				e = new RecordDescription(ElementDescription.newId(), e.name(), List.of(e), null, Occurrence.ONCE,
						Semantics.NONE);
			}
			if (e instanceof FieldDescription f) {
				return f.withOccurrence(occurrence);
			}
			if (e instanceof RecordDescription r) {
				return r.withOccurrence(occurrence);
			}
			return ((ChoiceDescription) e).withOccurrence(occurrence);
		}
	}

	/** The control characters of EAST's predefined ASCII package, by name. */
	private static final Map<String, Integer> ASCII = asciiNames();

	private static Map<String, Integer> asciiNames() {
		String[] names = {"nul", "soh", "stx", "etx", "eot", "enq", "ack", "bel", "bs", "ht", "lf", "vt", "ff", "cr",
				"so", "si", "dle", "dc1", "dc2", "dc3", "dc4", "nak", "syn", "etb", "can", "em", "sub", "esc", "fs", "gs",
				"rs", "us"};
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < names.length; i++) {
			map.put(names[i], i);
		}
		map.put("del", 127);
		return Map.copyOf(map);
	}
}
