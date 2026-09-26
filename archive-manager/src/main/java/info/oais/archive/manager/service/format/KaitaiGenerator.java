package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import org.springframework.stereotype.Component;

/**
 * Generates a Kaitai Struct {@code .ksy} definition (https://doc.kaitai.io/ksy_reference.html)
 * from a {@link FormatDefinition}. Only applies to {@link FormatDefinitionKind#BYTE_LAYOUT} --
 * Kaitai describes a sequential byte layout, which an HDF5-style logical
 * group/dataset/attribute tree isn't (see {@code FormatTemplates.hdf5()}'s
 * own doc comment for why).
 *
 * <p>Hand-built text, not a generic YAML serializer, the same way every
 * SPARQL query elsewhere in this codebase is hand-built text: a serializer's
 * default key ordering wouldn't match Kaitai's own {@code meta:}/{@code seq:}
 * idiom, and this keeps the output exactly as intended without a mapping layer.
 */
@Component
public class KaitaiGenerator {

    /** @return the generated {@code .ksy} YAML text, or {@code null} if {@code def} isn't byte-layout. */
    public String generate(FormatDefinition def) {
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("meta:\n");
        sb.append("  id: ").append(FormatIdentifiers.snakeCase(def.getName())).append('\n');
        sb.append("  endian: ").append(def.getDefaultByteOrder() == ByteOrder.LITTLE_ENDIAN ? "le" : "be").append('\n');
        if (!def.getNotes().isBlank()) {
            sb.append("doc: |\n");
            appendIndentedLines(sb, def.getNotes(), "  ");
        }
        sb.append("seq:\n");
        for (FormatField field : def.getFields()) {
            sb.append("  - id: ").append(FormatIdentifiers.snakeCase(field.name())).append('\n');
            appendTypeAndSize(sb, field, def.getDefaultByteOrder());
            if (field.definition() != null && !field.definition().isBlank()) {
                sb.append("    doc: ").append(yamlQuote(field.definition())).append('\n');
            }
        }
        return sb.toString();
    }

    private void appendTypeAndSize(StringBuilder sb, FormatField field, ByteOrder defaultOrder) {
        String endianSuffix = field.byteOrder() == null || field.byteOrder() == defaultOrder
                ? "" : (field.byteOrder() == ByteOrder.LITTLE_ENDIAN ? "le" : "be");
        switch (field.type()) {
            case INT8 -> sb.append("    type: s1\n");
            case UINT8 -> sb.append("    type: u1\n");
            case INT16 -> sb.append("    type: s2").append(endianSuffix).append('\n');
            case UINT16 -> sb.append("    type: u2").append(endianSuffix).append('\n');
            case INT32 -> sb.append("    type: s4").append(endianSuffix).append('\n');
            case UINT32 -> sb.append("    type: u4").append(endianSuffix).append('\n');
            case INT64 -> sb.append("    type: s8").append(endianSuffix).append('\n');
            case UINT64 -> sb.append("    type: u8").append(endianSuffix).append('\n');
            case FLOAT32 -> sb.append("    type: f4").append(endianSuffix).append('\n');
            case FLOAT64 -> sb.append("    type: f8").append(endianSuffix).append('\n');
            case ASCII_STRING -> {
                sb.append("    type: str\n");
                sb.append("    size: ").append(field.lengthBytes() == null ? 1 : field.lengthBytes()).append('\n');
                sb.append("    encoding: ASCII\n");
            }
            case BYTES -> sb.append("    size: ").append(field.lengthBytes() == null ? 1 : field.lengthBytes()).append('\n');
        }
    }

    private void appendIndentedLines(StringBuilder sb, String text, String indent) {
        for (String line : text.split("\n", -1)) {
            sb.append(indent).append(line).append('\n');
        }
    }

    /** A YAML single-quoted scalar: only {@code '} itself needs escaping (doubled), unlike double-quoted YAML. */
    private String yamlQuote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
