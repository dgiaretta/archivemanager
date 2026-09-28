package info.oais.infomodel.structure.description;

/**
 * A field's value type, independent of any engine. In a binary record the
 * numeric types have a fixed width ({@link #fixedWidth()}); {@link #STRING}
 * and {@link #BYTES} need a length. In a delimited-text record every value is
 * text between delimiters, and the type says how to read it: {@code INT*} and
 * {@code UINT*} as a decimal integer, {@code FLOAT*} as a decimal number,
 * {@link #STRING} as-is ({@link #BYTES} isn't allowed there).
 */
public enum PrimitiveType {
	INT8(1), UINT8(1), INT16(2), UINT16(2), INT32(4), UINT32(4), INT64(8), UINT64(8),
	FLOAT32(4), FLOAT64(8),
	/** ASCII text. */
	STRING(0),
	/** Raw bytes, not interpreted. */
	BYTES(0);

	private final int width;

	PrimitiveType(int width) {
		this.width = width;
	}

	/** The width in bytes of a numeric type in a binary record; 0 for {@link #STRING}/{@link #BYTES}. */
	public int fixedWidth() {
		return width;
	}

	public boolean isInteger() {
		return ordinal() <= UINT64.ordinal();
	}

	public boolean isFloat() {
		return this == FLOAT32 || this == FLOAT64;
	}

	public boolean isNumeric() {
		return isInteger() || isFloat();
	}

	public boolean isSigned() {
		return this == INT8 || this == INT16 || this == INT32 || this == INT64 || isFloat();
	}

	/** Whether a binary field of this type needs an explicit length. */
	public boolean needsLength() {
		return width == 0;
	}
}
