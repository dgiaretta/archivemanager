package info.oais.infomodel.structure.description;

import info.oais.infomodel.structure.description.Expression.Binary;
import info.oais.infomodel.structure.description.Expression.FieldRef;
import info.oais.infomodel.structure.description.Expression.IntLiteral;
import info.oais.infomodel.structure.description.Expression.Op;
import info.oais.infomodel.structure.description.Expression.StringLiteral;
import info.oais.infomodel.structure.description.Expression.Unary;

/**
 * Recursive-descent parser for {@link Expression#parse}. Precedence, loosest
 * first: {@code or}, {@code and}, {@code not}, comparisons, {@code + -},
 * {@code * / %}, unary minus.
 */
final class ExpressionParser {

	/** The spelling {@link Expression#text()} uses - the same syntax {@link #parseAll()} accepts. */
	static final Expression.Syntax CANONICAL = new Expression.Syntax() {
		@Override
		public String intLiteral(long value) {
			return Long.toString(value);
		}

		@Override
		public String stringLiteral(String value) {
			return "'" + value.replace("'", "''") + "'";
		}

		@Override
		public String fieldRef(FieldRef ref) {
			return ref.name();
		}

		@Override
		public String binary(Op op) {
			return switch (op) {
				case OR -> "or";
				case AND -> "and";
				case EQ -> "=";
				case NE -> "!=";
				case LT -> "<";
				case LE -> "<=";
				case GT -> ">";
				case GE -> ">=";
				case ADD -> "+";
				case SUB -> "-";
				case MUL -> "*";
				case DIV -> "/";
				case MOD -> "%";
				default -> throw new IllegalArgumentException(op + " is not binary");
			};
		}

		@Override
		public String unary(Op op, String operand) {
			return (op == Op.NOT ? "not " : "-") + operand;
		}
	};

	private final String text;
	private int pos;

	ExpressionParser(String text) {
		this.text = text == null ? "" : text;
	}

	Expression parseAll() {
		skipSpace();
		if (pos >= text.length()) {
			throw error("Empty expression");
		}
		Expression e = or();
		skipSpace();
		if (pos < text.length()) {
			throw error("Unexpected '" + text.charAt(pos) + "'");
		}
		return e;
	}

	private Expression or() {
		Expression left = and();
		while (keyword("or")) {
			left = new Binary(Op.OR, left, and());
		}
		return left;
	}

	private Expression and() {
		Expression left = not();
		while (keyword("and")) {
			left = new Binary(Op.AND, left, not());
		}
		return left;
	}

	private Expression not() {
		if (keyword("not")) {
			return new Unary(Op.NOT, not());
		}
		return comparison();
	}

	private Expression comparison() {
		Expression left = additive();
		Op op;
		if (symbol("<=")) {
			op = Op.LE;
		} else if (symbol(">=")) {
			op = Op.GE;
		} else if (symbol("!=")) {
			op = Op.NE;
		} else if (symbol("==") || symbol("=")) {
			op = Op.EQ;
		} else if (symbol("<")) {
			op = Op.LT;
		} else if (symbol(">")) {
			op = Op.GT;
		} else {
			return left;
		}
		return new Binary(op, left, additive());
	}

	private Expression additive() {
		Expression left = multiplicative();
		while (true) {
			if (symbol("+")) {
				left = new Binary(Op.ADD, left, multiplicative());
			} else if (symbol("-")) {
				left = new Binary(Op.SUB, left, multiplicative());
			} else {
				return left;
			}
		}
	}

	private Expression multiplicative() {
		Expression left = unary();
		while (true) {
			if (symbol("*")) {
				left = new Binary(Op.MUL, left, unary());
			} else if (symbol("/")) {
				left = new Binary(Op.DIV, left, unary());
			} else if (symbol("%")) {
				left = new Binary(Op.MOD, left, unary());
			} else {
				return left;
			}
		}
	}

	private Expression unary() {
		if (symbol("-")) {
			Expression operand = unary();
			if (operand instanceof IntLiteral lit) {
				return new IntLiteral(-lit.value());
			}
			return new Unary(Op.NEG, operand);
		}
		return primary();
	}

	private Expression primary() {
		skipSpace();
		if (pos >= text.length()) {
			throw error("Expression ends too early");
		}
		char c = text.charAt(pos);
		if (c == '(') {
			pos++;
			Expression inner = or();
			if (!symbol(")")) {
				throw error("Missing ')'");
			}
			return inner;
		}
		if (c == '\'' || c == '"') {
			return new StringLiteral(quoted(c));
		}
		if (Character.isDigit(c)) {
			int start = pos;
			if (text.startsWith("0x", pos) || text.startsWith("0X", pos)) {
				pos += 2;
				while (pos < text.length() && Character.digit(text.charAt(pos), 16) >= 0) {
					pos++;
				}
				return new IntLiteral(parseLong(text.substring(start + 2, pos), 16, start));
			}
			while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
				pos++;
			}
			return new IntLiteral(parseLong(text.substring(start, pos), 10, start));
		}
		if (Character.isLetter(c) || c == '_') {
			int start = pos;
			while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_'
					|| (text.charAt(pos) == '.' && pos + 1 < text.length()
							&& (Character.isLetter(text.charAt(pos + 1)) || text.charAt(pos + 1) == '_')))) {
				pos++;
			}
			// A dotted name (header.length) is a field inside a record read earlier.
			String name = text.substring(start, pos);
			if (name.equals("and") || name.equals("or") || name.equals("not")) {
				pos = start;
				throw error("'" + name + "' needs something before it");
			}
			return new FieldRef(name);
		}
		throw error("Unexpected '" + c + "'");
	}

	private String quoted(char quote) {
		StringBuilder sb = new StringBuilder();
		pos++;
		while (pos < text.length()) {
			char c = text.charAt(pos++);
			if (c == quote) {
				if (pos < text.length() && text.charAt(pos) == quote) {
					sb.append(quote);
					pos++;
				} else {
					return sb.toString();
				}
			} else {
				sb.append(c);
			}
		}
		throw error("Unterminated text in quotes");
	}

	private long parseLong(String digits, int radix, int start) {
		try {
			return Long.parseLong(digits, radix);
		} catch (NumberFormatException e) {
			pos = start;
			throw error("Number out of range");
		}
	}

	private boolean keyword(String word) {
		skipSpace();
		int end = pos + word.length();
		if (text.regionMatches(true, pos, word, 0, word.length())
				&& (end == text.length() || !(Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_'))) {
			pos = end;
			return true;
		}
		return false;
	}

	private boolean symbol(String s) {
		skipSpace();
		if (text.startsWith(s, pos)) {
			pos += s.length();
			return true;
		}
		return false;
	}

	private void skipSpace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
			pos++;
		}
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at position " + (pos + 1) + " of \"" + text + "\"");
	}
}
