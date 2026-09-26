package info.oais.archive.manager.model.format;

/**
 * One field in a byte-layout format description -- the unit both the Kaitai
 * and DFDL generators walk sequentially, in file order, to build their
 * respective {@code seq:}/{@code xs:sequence}.
 *
 * @param lengthBytes  required for {@link FieldType#ASCII_STRING}/{@link FieldType#BYTES}
 *                     (those have no fixed width of their own); ignored for the
 *                     fixed-width numeric types, whose width is implied by the type.
 * @param byteOrder    overrides the format's default for this one field; null means
 *                     "use the format's default".
 * @param semanticName the concept this field's value represents (e.g. "Temperature"),
 *                      distinct from {@code name}, the structural identifier the
 *                      generators emit (e.g. {@code temp_k}); null falls back to
 *                      {@code name} wherever a semantic label is needed, such as
 *                      {@code rdfs:label} on this field's Semantic Representation
 *                      Information individual (see {@code FormatDescriptionRdfService}).
 * @param definition   what this field's value means, in prose -- also used verbatim as
 *                      the generators' {@code doc:}/{@code xs:documentation} comment.
 */
public record FormatField(String name, FieldType type, Integer lengthBytes, ByteOrder byteOrder,
                           String semanticName, String definition, String units) {

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
