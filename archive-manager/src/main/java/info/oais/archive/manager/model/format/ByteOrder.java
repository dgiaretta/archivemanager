package info.oais.archive.manager.model.format;

/**
 * Endianness for a multi-byte field. Ignored for the single-byte types
 * ({@link FieldType#INT8}/{@link FieldType#UINT8}) and for
 * {@link FieldType#ASCII_STRING}/{@link FieldType#BYTES}, which have no byte
 * order of their own.
 */
public enum ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN
}
