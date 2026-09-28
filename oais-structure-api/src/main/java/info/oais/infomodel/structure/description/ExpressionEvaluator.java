package info.oais.infomodel.structure.description;

import java.math.BigInteger;
import java.util.function.Function;

/**
 * Evaluates an {@link Expression} against field values already decoded, with
 * the same integer semantics every engine uses: division and remainder
 * truncate toward zero (Java, Kaitai Struct, XQuery {@code idiv}/{@code mod}).
 */
public final class ExpressionEvaluator {

	private ExpressionEvaluator() {
	}

	/**
	 * @param values a field's value by reference, as resolved from where the
	 *               expression is used; null when not (yet) known
	 * @return a {@link BigInteger}, {@link String} or {@link Boolean}
	 * @throws IllegalArgumentException if a referenced value is missing or of the wrong kind
	 */
	public static Object evaluate(Expression e, Function<Expression.FieldRef, Object> values) {
		if (e instanceof Expression.IntLiteral lit) {
			return BigInteger.valueOf(lit.value());
		}
		if (e instanceof Expression.StringLiteral lit) {
			return lit.value();
		}
		if (e instanceof Expression.FieldRef ref) {
			Object v = values.apply(ref);
			if (v == null) {
				throw new IllegalArgumentException("No value for '" + ref.name() + "'");
			}
			return normalise(v);
		}
		if (e instanceof Expression.Unary u) {
			Object v = evaluate(u.operand(), values);
			return u.op() == Expression.Op.NOT ? !asBoolean(v) : asInteger(v).negate();
		}
		Expression.Binary b = (Expression.Binary) e;
		if (b.op() == Expression.Op.AND) {
			return asBoolean(evaluate(b.left(), values)) && asBoolean(evaluate(b.right(), values));
		}
		if (b.op() == Expression.Op.OR) {
			return asBoolean(evaluate(b.left(), values)) || asBoolean(evaluate(b.right(), values));
		}
		Object l = evaluate(b.left(), values);
		Object r = evaluate(b.right(), values);
		if (b.op().isComparison()) {
			int cmp = l instanceof BigInteger li && r instanceof BigInteger ri ? li.compareTo(ri)
					: l.toString().compareTo(r.toString());
			return switch (b.op()) {
				case EQ -> cmp == 0;
				case NE -> cmp != 0;
				case LT -> cmp < 0;
				case LE -> cmp <= 0;
				case GT -> cmp > 0;
				default -> cmp >= 0;
			};
		}
		BigInteger li = asInteger(l);
		BigInteger ri = asInteger(r);
		return switch (b.op()) {
			case ADD -> li.add(ri);
			case SUB -> li.subtract(ri);
			case MUL -> li.multiply(ri);
			case DIV -> li.divide(ri);
			default -> li.remainder(ri);
		};
	}

	private static Object normalise(Object v) {
		if (v instanceof BigInteger || v instanceof String || v instanceof Boolean) {
			return v;
		}
		if (v instanceof Number n) {
			return new java.math.BigDecimal(n.toString()).toBigIntegerExact();
		}
		return v.toString();
	}

	private static BigInteger asInteger(Object v) {
		if (v instanceof BigInteger i) {
			return i;
		}
		try {
			return new BigInteger(v.toString().strip());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + v + "' isn't a whole number");
		}
	}

	private static boolean asBoolean(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		return !asInteger(v).equals(BigInteger.ZERO);
	}
}
