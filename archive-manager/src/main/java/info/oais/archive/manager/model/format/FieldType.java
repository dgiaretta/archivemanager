package info.oais.archive.manager.model.format;

/**
 * A byte-layout field's primitive type, independent of which target format
 * describes it. The numeric types have an implied fixed width (see
 * {@link FormatField#fixedWidthBytes()}); {@link #ASCII_STRING} and
 * {@link #BYTES} don't, and need {@link FormatField#lengthBytes()} set.
 */
public enum FieldType {
    INT8, UINT8, INT16, UINT16, INT32, UINT32, INT64, UINT64, FLOAT32, FLOAT64, ASCII_STRING, BYTES
}
