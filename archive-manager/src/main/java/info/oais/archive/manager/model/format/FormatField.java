package info.oais.archive.manager.model.format;

/**
 * One field in a byte-layout format description -- the unit both the Kaitai
 * and DFDL generators walk sequentially, in file order, to build their
 * respective {@code seq:}/{@code xs:sequence}.
 *
 * @param lengthBytes required for {@link FieldType#ASCII_STRING}/{@link FieldType#BYTES}
 *                    (those have no fixed width of their own); ignored for the
 *                    fixed-width numeric types, whose width is implied by the type.
 * @param byteOrder   overrides the format's default for this one field; null means
 *                    "use the format's default".
 */
public record FormatField(String name, FieldType type, Integer lengthBytes, ByteOrder byteOrder,
                           String description, String units) {

    /** This field's width in bytes, for the fixed-width numeric types; null for string/bytes (see {@link #lengthBytes}). */
    public Integer fixedWidthBytes() {
        return switch (type) {
            case INT8, UINT8 -> 1;
            case INT16, UINT16 -> 2;
            case INT32, UINT32, FLOAT32 -> 4;
            case INT64, UINT64, FLOAT64 -> 8;
            case ASCII_STRING, BYTES -> null;
        };
    }
}
