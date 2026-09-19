package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.model.format.Hdf5Node;
import org.springframework.stereotype.Component;

/**
 * Generates a DRB description for either of the two real, unrelated DRB
 * implementations (see {@link DrbTarget}'s doc comment): {@code drb-python}
 * (https://gitlab.com/drb-python), as a {@code .py} scaffold, or the
 * original Java DRB ({@code fr.gael.drb}), as a {@code .java} field-reference
 * class in the same reflection-based shape as
 * {@code oais-structure-adapters}' {@code oais-structure-drb} module
 * ({@code DrbStructureNode}/{@code DrbFormatSpecification}), for projects
 * that already depend on it.
 *
 * <p>Neither target has a declarative per-format schema language the way
 * Kaitai/DFDL do -- DRB normally auto-detects a format from content and
 * exposes the result as a node tree at runtime. Both generated outputs are
 * therefore a field-semantics reference plus a minimal usage snippet, not a
 * schema DRB itself reads, and both say so in their own generated comments.
 */
@Component
public class DrbGenerator {

    public String generate(FormatDefinition def, DrbTarget target) {
        boolean byteLayout = def.getKind() == FormatDefinitionKind.BYTE_LAYOUT;
        return switch (target) {
            case PYTHON -> byteLayout ? generatePythonByteLayout(def) : generatePythonLogicalTree(def);
            case JAVA -> byteLayout ? generateJavaByteLayout(def) : generateJavaLogicalTree(def);
        };
    }

    private String generatePythonByteLayout(FormatDefinition def) {
        String className = toPascalCase(def.getName()) + "Node";
        StringBuilder fieldTable = new StringBuilder();
        StringBuilder properties = new StringBuilder();
        for (FormatField field : def.getFields()) {
            String pyName = FormatIdentifiers.snakeCase(field.name());
            Integer width = field.fixedWidthBytes();
            int lengthBytes = width != null ? width : (field.lengthBytes() == null ? 1 : field.lengthBytes());
            ByteOrder order = field.byteOrder() == null ? def.getDefaultByteOrder() : field.byteOrder();
            fieldTable.append("    (\"%s\", \"%s\", %d, \"%s\"),\n".formatted(
                    pyName, field.type().name().toLowerCase(), lengthBytes, order == ByteOrder.LITTLE_ENDIAN ? "little" : "big"));
            properties.append("""

                    @property
                    def %s(self):
                        \"\"\"%s\"\"\"
                        return self._read_field("%s")
                    """.formatted(pyName, pyDocstring(field.description()), pyName));
        }

        return """
                \"\"\"
                Auto-generated DRB (drb-python, https://gitlab.com/drb-python) description
                for "%s".

                THIS IS A STARTING SCAFFOLD, NOT A VERIFIED DRIVER: drb-python has no
                declarative schema language of its own the way Kaitai/DFDL do -- format
                support there is normally a DrbNode implementation, written against
                drb-core. Adapt the base class / method signatures below to whichever
                drb-core version you actually have installed before relying on this.
                %s
                \"\"\"

                # (field name, type, length in bytes, byte order)
                FIELDS = [
                %s]


                class %s:
                    \"\"\"DrbNode-shaped accessor over this format's fields.\"\"\"

                    def __init__(self, source):
                        self._source = source
                %s
                    def _read_field(self, name):
                        raise NotImplementedError(
                            "wire this up to the real byte-reading logic for your source, "
                            "using FIELDS above for each field's offset/width/byte order"
                        )
                """.formatted(def.getName(), notesBlock(def.getNotes()), fieldTable, className, properties);
    }

    private String generatePythonLogicalTree(FormatDefinition def) {
        StringBuilder schema = new StringBuilder();
        for (Hdf5Node node : def.getNodes()) {
            schema.append(pySchemaRow(node));
        }

        return """
                \"\"\"
                Auto-generated DRB (drb-python, https://gitlab.com/drb-python) logical
                schema description for "%s".

                drb-python already has a mature HDF5 driver (drb-driver-hdf5) that reads a
                real .h5 file's actual group/dataset/attribute tree at runtime -- this
                module does NOT reimplement that. It documents the EXPECTED schema and
                field semantics for this kind of file, in the same group/dataset/attribute
                vocabulary drb-driver-hdf5's DrbNode tree exposes (.children, .attributes,
                .name, .value), as a semantic reference alongside the archive's own
                SemanticRepresentationInformation entry.
                %s
                \"\"\"

                # kind is one of "group", "dataset", "attribute"; path uses "/" for
                # group/dataset nesting and "@" for an attribute on the preceding path.
                SCHEMA = [
                %s]
                """.formatted(def.getName(), notesBlock(def.getNotes()), schema);
    }

    private String pySchemaRow(Hdf5Node node) {
        StringBuilder row = new StringBuilder();
        row.append("    {\"kind\": \"%s\", \"path\": \"%s\"".formatted(node.kind().name().toLowerCase(), pyEscape(node.path())));
        if (node.dtype() != null) {
            row.append(", \"dtype\": \"%s\"".formatted(pyEscape(node.dtype())));
        }
        if (node.shape() != null && !node.shape().isEmpty()) {
            row.append(", \"shape\": %s".formatted(node.shape()));
        }
        if (node.description() != null && !node.description().isBlank()) {
            row.append(", \"description\": \"%s\"".formatted(pyEscape(node.description())));
        }
        row.append("},\n");
        return row.toString();
    }

    /**
     * Java DRB ({@code fr.gael.drb}) target: a field-semantics reference plus
     * a {@code DrbFormatSpecification} usage snippet, in the same package and
     * reflection-based spirit as {@code oais-structure-drb}'s own
     * {@code DrbStructureNode}/{@code DrbFormatSpecification} classes -- not
     * a reimplementation of them. Most callers only need DRB's default,
     * auto-detecting resolver; this exists for the field documentation and
     * for the (rarer) case a format needs an explicit factory resolver or
     * protocol hint.
     */
    private String generateJavaByteLayout(FormatDefinition def) {
        String className = toPascalCase(def.getName()) + "DrbFields";
        StringBuilder fieldDocs = new StringBuilder();
        StringBuilder fieldConstants = new StringBuilder();
        for (FormatField field : def.getFields()) {
            String javaName = toScreamingSnakeCase(field.name());
            fieldDocs.append(" *   - %s: %s\n".formatted(field.name(), javaDocLine(field.description())));
            fieldConstants.append("    /** %s */\n    public static final String %s = \"%s\";\n"
                    .formatted(javaDocLine(field.description()), javaName, javaStringEscape(field.name())));
        }

        return """
                package info.oais.infomodel.structure.drb.generated;

                import info.oais.infomodel.structure.drb.DrbFormatSpecification;

                /**
                 * DRB (Java, fr.gael.drb -- consumed by reflection, see oais-structure-drb's
                 * ReflectiveApi/DrbStructureNode) field reference for "%s".
                 *
                 * DRB auto-detects a Digital Object's format from its content and exposes the
                 * result as a DrbNode tree at runtime (name/value/children/attributes) -- there
                 * is no separate declarative schema file to write per format the way there is
                 * for Kaitai/DFDL, so this is a field-semantics reference and a
                 * DrbFormatSpecification usage snippet, not a schema DRB itself reads.
                 *
                %s *
                 * Adjust the package above to fit your project (e.g. alongside
                 * oais-structure-drb if that is where this is used).
                 */
                public final class %s {

                    private %s() {
                    }

                %s
                    /**
                     * Most callers only need the default, auto-detecting resolver:
                     * {@code new DrbFormatSpecification()}. Construct it with an explicit
                     * factory resolver class name / protocol hint only if this format needs
                     * to bypass or confirm auto-detection.
                     */
                    public static DrbFormatSpecification defaultSpecification() {
                        return new DrbFormatSpecification();
                    }
                }
                """.formatted(def.getName(), fieldDocs, className, className, fieldConstants);
    }

    private String generateJavaLogicalTree(FormatDefinition def) {
        String className = toPascalCase(def.getName()) + "DrbSchema";
        StringBuilder schemaDocs = new StringBuilder();
        for (Hdf5Node node : def.getNodes()) {
            schemaDocs.append(" *   - %s (%s)%s: %s\n".formatted(
                    node.path(), node.kind().name().toLowerCase(),
                    node.dtype() != null ? " [" + node.dtype() + "]" : "",
                    javaDocLine(node.description())));
        }

        return """
                package info.oais.infomodel.structure.drb.generated;

                /**
                 * DRB (Java, fr.gael.drb) logical schema reference for "%s".
                 *
                 * fr.gael.drb already has a real HDF5 driver that reads an actual .h5 file's
                 * group/dataset/attribute tree at runtime via the DrbNode API -- see
                 * oais-structure-drb's DrbStructureNode (getName/getChildrenList/
                 * getAttributesList/getValue, all reached by reflection). This class does NOT
                 * reimplement that; it documents the EXPECTED schema and field semantics for
                 * this kind of file, for reference alongside the archive's own
                 * SemanticRepresentationInformation entry.
                 *
                %s *
                 * Adjust the package above to fit your project.
                 */
                public final class %s {

                    private %s() {
                    }
                }
                """.formatted(def.getName(), schemaDocs, className, className);
    }

    private String notesBlock(String notes) {
        return notes == null || notes.isBlank() ? "" : "\n" + notes;
    }

    private String pyDocstring(String description) {
        return description == null || description.isBlank() ? "(no description provided)" : description.replace("\"\"\"", "'''");
    }

    private String pyEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String javaDocLine(String description) {
        String text = description == null || description.isBlank() ? "(no description provided)" : description;
        return text.replace("*/", "* /").replace("\n", " ");
    }

    private String javaStringEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toPascalCase(String name) {
        String snake = FormatIdentifiers.snakeCase(name);
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.isEmpty() ? "Format" : sb.toString();
    }

    private String toScreamingSnakeCase(String name) {
        return FormatIdentifiers.snakeCase(name).toUpperCase();
    }
}
