package info.oais.infomodel.structure.east;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses an EAST Data Description Record (CCSDS 644.0-B-3) - its logical
 * package and, if there is one, its physical package - into the syntax tree
 * {@link EastReader}, {@link EastWriter}'s tests and {@link EastInterpreter}
 * work from. It checks only what it needs to understand a description: what
 * a description means is left to whoever uses the tree.
 */
final class EastParser {

	/** A parsed description: its logical package and its physical package (null if there's none). */
	record Description(Pkg logical, Pkg physical) {
	}

	/**
	 * Adds EAST's predefined types to a logical package: CHARACTER (8 bits),
	 * STRING (an array of characters) and - leniently, since the
	 * specification's own examples use it - BOOLEAN (FALSE, TRUE).
	 */
	static void predefine(Pkg logical) {
		EnumTy character = new EnumTy("character", 0, List.of(), List.of());
		character.size = 8L;
		EnumTy bool = new EnumTy("boolean", 0, List.of("false", "true"), List.of("FALSE", "TRUE"));
		logical.types.putIfAbsent("character", character);
		logical.types.putIfAbsent("boolean", bool);
		logical.types.putIfAbsent("string", new ArrTy("string", 0,
				List.of(new Index("positive", null, null, true)), "character"));
	}

	/**
	 * @throws EastException if the text isn't an EAST description
	 */
	static Description parse(String text) {
		EastParser parser = new EastParser(text);
		Pkg logical = parser.parsePackage();
		Pkg physical = parser.at(K.END) ? null : parser.parsePackage();
		if (!parser.at(K.END)) {
			throw parser.error("Expected the end of the description after the physical package");
		}
		return new Description(logical, physical);
	}

	// ------------------------------------------------------------------ lexer

	enum K {
		IDENT, NUM, STRING, CHAR, SYM, END
	}

	/** A token; {@code s} is lower case for identifiers, {@code raw} as written. */
	record Tok(K k, String s, String raw, BigDecimal num, boolean real, int line) {
	}

	private static final Set<String> TWO_CHAR = Set.of("=>", "..", "**", ":=", "/=", ">=", "<=", "<>");

	private final List<Tok> toks;
	private int pos;

	private EastParser(String text) {
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

	sealed interface Node permits Num, Str, Chr, Name, Bin, Un, Call, Agg {
	}

	record Num(BigDecimal value, boolean real) implements Node {
	}

	record Str(String value) implements Node {
	}

	record Chr(char value) implements Node {
	}

	/** A name or an EAST path ({@code PACKET.PRIMARY_HEADER.LENGTH}), lower case, with the spelling as written. */
	record Name(List<String> path, String raw, int line) implements Node {
		String joined() {
			return String.join(".", path);
		}

		String last() {
			return path.get(path.size() - 1);
		}
	}

	record Bin(String op, Node left, Node right, int line) implements Node {
	}

	record Un(String op, Node operand, int line) implements Node {
	}

	record Call(String function, List<Node> args, int line) implements Node {
	}

	/** An aggregate: {@code (a, b)} or {@code (X => a, others => b)}; keys are null for positional items. */
	record Agg(List<Item> items) implements Node {
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
	record Index(String typeName, Node lo, Node hi, boolean unconstrained) {
	}

	/** A value, or range of values, a variant alternative is chosen by; or {@code others}. */
	record Choice(Node value, Node hi, boolean others) {
	}

	abstract static class Ty {
		final String name;
		final int line;
		Long size;

		Ty(String name, int line) {
			this.name = name;
			this.line = line;
		}
	}

	static final class EnumTy extends Ty {
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

	static final class IntTy extends Ty {
		final Node lo;
		final Node hi;

		IntTy(String name, int line, Node lo, Node hi) {
			super(name, line);
			this.lo = lo;
			this.hi = hi;
		}
	}

	static final class RealTy extends Ty {
		final Node lo;
		final Node hi;

		RealTy(String name, int line, Node lo, Node hi) {
			super(name, line);
			this.lo = lo;
			this.hi = hi;
		}
	}

	static final class ArrTy extends Ty {
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

	record Discr(String name, String type, int line) {
		boolean virtual() {
			return name.startsWith("virtual_");
		}
	}

	record Comp(String name, String type, List<Index> constraint, Node value, boolean constant, int line) {
	}

	record When(List<Choice> choices, List<Comp> comps, Variant nested, int line) {
	}

	record Variant(String discriminant, List<When> whens, int line) {
		void names(List<String> into) {
			for (When w : whens) {
				w.comps().forEach(c -> into.add(c.name()));
				if (w.nested() != null) {
					w.nested().names(into);
				}
			}
		}
	}

	static final class RecTy extends Ty {
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

	static final class SubTy extends Ty {
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

	record Const(String type, Node value, int line) {
	}

	record Var(String name, String type, List<Index> constraint, int line) {
	}

	static final class Pkg {
		String name;
		String raw;
		final Map<String, Ty> types = new LinkedHashMap<>();
		final Map<String, Const> constants = new LinkedHashMap<>();
		final List<Var> vars = new ArrayList<>();
		final Map<String, Node> actuals = new HashMap<>();
		final Map<String, Integer> actualLines = new HashMap<>();
		/** Markers, by the variable each one ends the repetition of. */
		final Map<String, Const> markers = new LinkedHashMap<>();
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
				String repeated = pkg.vars.get(pkg.vars.size() - 1).name();
				if (pkg.markers.containsKey(repeated)) {
					throw new EastException(line, "'" + repeated.toUpperCase(Locale.ROOT) + "' already has a marker");
				}
				if (type == null || value == null) {
					throw new EastException(line, "A marker needs a type and a value");
				}
				pkg.markers.put(repeated, new Const(type, value, line));
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
	static BigDecimal staticValue(Pkg pkg, Node n) {
		return staticValue(pkg, n, 0);
	}

	static BigDecimal staticValue(Pkg pkg, Node n, int depth) {
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

	static boolean isWhole(BigDecimal v) {
		return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
	}

	/** The control characters of EAST's predefined ASCII package, by name. */
	static final Map<String, Integer> ASCII = asciiNames();

	static Map<String, Integer> asciiNames() {
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

