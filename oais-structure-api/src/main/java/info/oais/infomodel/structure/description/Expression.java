package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small expression language for the computed parts of a format
 * description: a repeat count ({@code n}, {@code rows * cols}), a length
 * ({@code name_len}, {@code n * 2 - 1}), an optional element's condition
 * ({@code flags = 1}) or a choice's discriminator ({@code kind}).
 *
 * <p>It has integer and string literals, references to earlier fields by
 * name, integer arithmetic ({@code + - * / %}), comparisons
 * ({@code = != < <= > >=}), {@code and}/{@code or}/{@code not}, and
 * parentheses - deliberately no functions and nothing engine-specific, so
 * every expression can be translated exactly into Kaitai Struct, DFDL
 * (DPath), DRB (XQuery) and Python. Expressions only ever reach generated
 * files through {@link #render}, never as the text a user typed.
 */
public sealed interface Expression extends Serializable {

	/** Operators, in the order of {@link #parse}'s precedence climbing (loosest first). */
	enum Op {
		OR, AND, EQ, NE, LT, LE, GT, GE, ADD, SUB, MUL, DIV, MOD, NEG, NOT;

		/** Whether this operator yields a boolean (so can't be used as a count or length). */
		public boolean isBoolean() {
			return this == OR || this == AND || this == NOT || isComparison();
		}

		public boolean isComparison() {
			return this == EQ || this == NE || this == LT || this == LE || this == GT || this == GE;
		}
	}

	record IntLiteral(long value) implements Expression {
	}

	record StringLiteral(String value) implements Expression {
		public StringLiteral {
			Objects.requireNonNull(value, "value");
		}
	}

	/** A reference, by name, to a field read earlier - see {@link Scope}. */
	record FieldRef(String name) implements Expression {
		public FieldRef {
			Objects.requireNonNull(name, "name");
		}
	}

	record Unary(Op op, Expression operand) implements Expression {
	}

	record Binary(Op op, Expression left, Expression right) implements Expression {
	}

	/** How one engine spells an expression; see {@link #render}. */
	interface Syntax {
		String intLiteral(long value);

		String stringLiteral(String value);

		/** @param ref a reference already checked to resolve (see {@link Scope}) */
		String fieldRef(FieldRef ref);

		/** The spelling of a binary operator, e.g. {@code "eq"} for DFDL's {@code =}. */
		String binary(Op op);

		/** The spelling of a unary operator applied to already-rendered {@code operand}. */
		String unary(Op op, String operand);
	}

	/** Renders this expression for one engine, fully parenthesised so no precedence rules are assumed. */
	default String render(Syntax syntax) {
		if (this instanceof IntLiteral lit) {
			return syntax.intLiteral(lit.value());
		}
		if (this instanceof StringLiteral lit) {
			return syntax.stringLiteral(lit.value());
		}
		if (this instanceof FieldRef ref) {
			return syntax.fieldRef(ref);
		}
		if (this instanceof Unary u) {
			return syntax.unary(u.op(), u.operand().render(syntax));
		}
		Binary b = (Binary) this;
		return "(" + b.left().render(syntax) + " " + syntax.binary(b.op()) + " " + b.right().render(syntax) + ")";
	}

	/** Every field this expression refers to, in order of appearance. */
	default List<FieldRef> fieldRefs() {
		List<FieldRef> refs = new ArrayList<>();
		collect(this, refs);
		return refs;
	}

	private static void collect(Expression e, List<FieldRef> into) {
		if (e instanceof FieldRef ref) {
			into.add(ref);
		} else if (e instanceof Unary u) {
			collect(u.operand(), into);
		} else if (e instanceof Binary b) {
			collect(b.left(), into);
			collect(b.right(), into);
		}
	}

	/** Whether this expression yields a boolean rather than a number or string. */
	default boolean isBoolean() {
		return (this instanceof Unary u && u.op() == Op.NOT) || (this instanceof Binary b && b.op().isBoolean());
	}

	/** The canonical text of this expression, which {@link #parse} reads back to an equal expression. */
	default String text() {
		return render(ExpressionParser.CANONICAL);
	}

	/**
	 * Parses expression text such as {@code n * 2 - 1} or
	 * {@code kind = 1 and flags != 0}.
	 *
	 * @throws IllegalArgumentException with a message pointing at the problem
	 */
	static Expression parse(String text) {
		return new ExpressionParser(text).parseAll();
	}
}
