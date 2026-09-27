package info.oais.infomodel.structure.semantic;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Best-effort coercion of a value to a declared target class - shared by
 * {@link StructureNodeBackedTable#getValueAt} (coercing what a
 * {@link ColumnMapping} extractor read out of a {@link info.oais.infomodel.structure.StructureNode})
 * and {@link OaisIfTableModel#setValueAt} (coercing what a Swing cell editor
 * hands back after an edit) - both cases are "some value arrived as one Java
 * type, but the column's declared class says another," and both want the
 * same tolerant handling: {@code null}, a value already an instance of
 * {@code targetClass}, and a {@code targetClass} this method does not know
 * how to produce are all returned/passed through unchanged; an unparsable
 * {@link String} is likewise returned unchanged rather than throwing.
 */
final class ValueCoercion {

	private ValueCoercion() {
	}

	static Object coerce(Object value, Class<?> targetClass) {
		if (value == null || targetClass.isInstance(value)) {
			return value;
		}
		if (targetClass == String.class) {
			return value.toString();
		}
		if (value instanceof Number number) {
			return coerceNumber(number, targetClass);
		}
		if (!(value instanceof String text)) {
			return value;
		}
		String trimmed = text.trim();
		try {
			if (targetClass == Integer.class || targetClass == int.class) {
				return Integer.valueOf(trimmed);
			}
			if (targetClass == Long.class || targetClass == long.class) {
				return Long.valueOf(trimmed);
			}
			if (targetClass == Short.class || targetClass == short.class) {
				return Short.valueOf(trimmed);
			}
			if (targetClass == Byte.class || targetClass == byte.class) {
				return Byte.valueOf(trimmed);
			}
			if (targetClass == Double.class || targetClass == double.class) {
				return Double.valueOf(trimmed);
			}
			if (targetClass == Float.class || targetClass == float.class) {
				return Float.valueOf(trimmed);
			}
			if (targetClass == BigInteger.class) {
				return new BigInteger(trimmed);
			}
			if (targetClass == BigDecimal.class) {
				return new BigDecimal(trimmed);
			}
			if (targetClass == Boolean.class || targetClass == boolean.class) {
				return Boolean.valueOf(trimmed);
			}
		} catch (NumberFormatException e) {
			// value doesn't parse as targetClass - fall through and return it unchanged
		}
		return value;
	}

	/**
	 * One numeric type to another (e.g. an adapter's {@code Long} into an
	 * {@code int} column), only when no information is lost; otherwise the
	 * value is returned unchanged, as for an unparseable string.
	 */
	private static Object coerceNumber(Number number, Class<?> targetClass) {
		try {
			if (targetClass == Double.class || targetClass == double.class) {
				return number.doubleValue();
			}
			if (targetClass == Float.class || targetClass == float.class) {
				return number.floatValue();
			}
			BigDecimal exact = new BigDecimal(number.toString());
			if (targetClass == Integer.class || targetClass == int.class) {
				return exact.intValueExact();
			}
			if (targetClass == Long.class || targetClass == long.class) {
				return exact.longValueExact();
			}
			if (targetClass == Short.class || targetClass == short.class) {
				return exact.shortValueExact();
			}
			if (targetClass == Byte.class || targetClass == byte.class) {
				return exact.byteValueExact();
			}
			if (targetClass == BigInteger.class) {
				return exact.toBigIntegerExact();
			}
			if (targetClass == BigDecimal.class) {
				return exact;
			}
		} catch (ArithmeticException | NumberFormatException e) {
			// out of range, or not a whole number, for targetClass
		}
		return number;
	}
}
