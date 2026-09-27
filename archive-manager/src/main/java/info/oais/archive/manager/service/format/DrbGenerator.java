package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.model.format.Hdf5Node;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a DRB description for either of the two real, unrelated DRB
 * implementations (see {@link DrbTarget}'s doc comment): {@code drb-python}
 * (https://gitlab.com/drb-python), or the original Java DRB
 * ({@code fr.gael.drb}), as a {@code .java} field-reference class in the same
 * reflection-based shape as {@code oais-structure-drb}
 * ({@code DrbStructureNode}/{@code DrbFormatSpecification}).
 *
 * <p>drb-python has no declarative schema language: a format is supported by
 * a driver package (a {@code DrbFactory} plus {@code DrbNode}s, registered
 * through entry points). For a byte-layout definition this therefore
 * generates a real, installable driver ({@link #pythonDriverPackage}) that
 * decodes the fields; {@code DrbPythonSampleRunner} and
 * {@code DrbPythonSampleRunnerTest} run it against actual bytes. A
 * logical-tree definition gets a documented schema only.
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

    /**
     * The driver package's import path under drb-python's {@code drb.drivers}/
     * {@code drb.topics} namespaces, and its entry-point name -- prefixed
     * {@code am_} so a generated driver can't collide with an official one
     * (e.g. {@code drb.drivers.csv}).
     */
    public String pythonDriverId(FormatDefinition def) {
        // Capped because the id appears twice in pip's build paths, and a long
        // format name can otherwise break an install on Windows' 260-character path limit.
        String snake = FormatIdentifiers.snakeCase(def.getName());
        if (snake.length() > MAX_DRIVER_ID_LENGTH) {
            snake = snake.substring(0, MAX_DRIVER_ID_LENGTH).replaceAll("_+$", "");
        }
        return "am_" + snake;
    }

    private static final int MAX_DRIVER_ID_LENGTH = 20;

    /** The {@code DrbFactory} subclass the generated driver module defines. */
    public String pythonFactoryClassName(FormatDefinition def) {
        return toPascalCase(def.getName()) + "Factory";
    }

    /** The pip distribution name of the package from {@link #pythonDriverPackage}. */
    public String pythonDistributionName(FormatDefinition def) {
        return "drb-driver-" + pythonDriverId(def).replace('_', '-');
    }

    /**
     * A complete, pip-installable drb-python driver package for a byte-layout
     * definition, as {@code relative path -> file content}: the driver module
     * itself ({@link #generate} with {@link DrbTarget#PYTHON}), a drb "topic"
     * ({@code cortex.ttl}) whose signature matches the definition's
     * {@link FormatDefinition#getFileExtensions() file extensions}, and a
     * {@code pyproject.toml} registering both through drb's {@code drb.driver}
     * and {@code drb.topic} entry points -- the same layout as drb-python's own
     * published drivers (e.g. {@code drb-driver-csv}). {@code null} for a
     * logical-tree definition, which has no byte layout to decode.
     */
    public Map<String, String> pythonDriverPackage(FormatDefinition def) {
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return null;
        }
        String id = pythonDriverId(def);
        String className = toPascalCase(def.getName());
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pyproject.toml", """
                [build-system]
                requires = ["setuptools>=61"]
                build-backend = "setuptools.build_meta"

                [project]
                name = "%s"
                version = "0.1.0"
                description = %s
                requires-python = ">=3.8"
                dependencies = ["drb>=2.4,<3"]

                [project.entry-points."drb.driver"]
                %s = "drb.drivers.%s:%sFactory"

                [project.entry-points."drb.topic"]
                %s = "drb.topics.%s"

                [tool.setuptools]
                packages = ["drb.drivers.%s", "drb.topics.%s"]

                [tool.setuptools.package-data]
                "drb.topics.%s" = ["cortex.ttl"]
                """.formatted(pythonDistributionName(def),
                quotedLiteral("drb-python driver for " + def.getName() + " (generated by archive-manager)"),
                id, id, className, id, id, id, id, id));
        files.put("drb/drivers/" + id + "/__init__.py", generatePythonByteLayout(def));
        files.put("drb/topics/" + id + "/__init__.py", "");
        files.put("drb/topics/" + id + "/cortex.ttl", pythonTopic(def, id));
        files.put("README.md", """
                # %s

                drb-python driver for **%s**, generated by archive-manager's RepInfo Tools
                from the same field list as its Kaitai Struct / DFDL descriptions.

                Install into the Python environment that has drb-python (`pip install drb`),
                straight from the downloaded zip:

                    pip install %s.zip

                (or `pip install .` from inside the unzipped folder).

                %s
                Or bypass detection and apply this driver to any file explicitly:

                    from drb.drivers.file.file import DrbFileFactory
                    from drb.drivers.%s import %sFactory
                    node = %sFactory().create(DrbFileFactory().create("path/to/file"))
                    for field in node:
                        print(field.name, field.value, field @ "offset")

                Each child node is one field, in file order. Its attributes carry the byte
                `offset`/`length`, the field `type`, and the `semantic_name`, `definition`
                and `units` recorded as Semantic Representation Information in the archive.
                """.formatted(pythonDistributionName(def), def.getName(), pythonDistributionName(def),
                def.getFileExtensions().isEmpty()
                        ? "No file extensions were given, so drb won't pick this driver automatically.\n"
                        : """
                        drb then selects this driver automatically for files ending in %s:

                            import drb.topics.resolver as resolver
                            node = resolver.create("path/to/file.%s")
                        """.formatted(String.join(", ", def.getFileExtensions().stream().map(e -> "." + e).toList()),
                        def.getFileExtensions().get(0)),
                id, className, className));
        return files;
    }

    private String pythonTopic(FormatDefinition def, String id) {
        // Character class rather than "\\." so the regex needs no backslash escaping inside Turtle.
        String signature = def.getFileExtensions().isEmpty() ? "" : """
                   drb:signature [ drb:nameMatch "(?i).+[.](%s)$" ] ;
                """.formatted(String.join("|", def.getFileExtensions()));
        return """
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix drb: <http://www.gael.fr/drb#> .

                drb:%s
                   a owl:Class ;
                   drb:id "%s" ;
                   drb:category "FORMATTING" ;
                   rdfs:subClassOf drb:item ;
                   rdfs:label %s ;
                %s   drb:implementationIdentifier "%s" .
                """.formatted(id, UUID.nameUUIDFromBytes(("archive-manager:drb-python:" + id).getBytes(StandardCharsets.UTF_8)),
                quotedLiteral(def.getName()), signature, id);
    }

    private String generatePythonByteLayout(FormatDefinition def) {
        String className = toPascalCase(def.getName());
        StringBuilder fieldTable = new StringBuilder();
        for (FormatField field : def.getFields()) {
            Integer width = field.fixedWidthBytes();
            int lengthBytes = width != null ? width : (field.lengthBytes() == null ? 1 : field.lengthBytes());
            ByteOrder order = field.byteOrder() == null ? def.getDefaultByteOrder() : field.byteOrder();
            fieldTable.append("    (%s, \"%s\", %d, \"%s\", %s, %s, %s),\n".formatted(
                    quotedLiteral(FormatIdentifiers.snakeCase(field.name())), field.type().name().toLowerCase(), lengthBytes,
                    order == ByteOrder.LITTLE_ENDIAN ? "little" : "big",
                    pyOptional(field.semanticName()), pyOptional(field.definition()), pyOptional(field.units())));
        }

        // Every user-entered string reaches the Python source only as an escaped
        // literal (quotedLiteral/pyOptional) -- never in a docstring or comment --
        // so nothing typed into the editor can end up as executable code.
        return """
                \"\"\"
                drb-python driver generated by archive-manager's RepInfo Tools.

                Decodes the sequential byte layout in FIELDS into a DrbNode tree: one child
                node per field, in file order, whose value is the decoded field and whose
                attributes carry its byte offset/length and type plus the semantic name,
                definition and units recorded as Semantic Representation Information.
                See the package's README.md for installing and using it.
                \"\"\"
                import io
                import struct
                from typing import Any, Dict, List, Tuple

                from drb.core import DrbFactory, DrbNode
                from drb.exceptions.core import DrbFactoryException
                from drb.nodes.abstract_node import AbstractNode
                from drb.nodes.logical_node import WrappedNode

                FORMAT_NAME = %s
                FORMAT_NOTES = %s

                # (name, type, length in bytes, byte order, semantic name, definition, units)
                FIELDS = [
                %s]

                _STRUCT = {"int8": "b", "uint8": "B", "int16": "h", "uint16": "H",
                           "int32": "i", "uint32": "I", "int64": "q", "uint64": "Q",
                           "float32": "f", "float64": "d"}


                def _decode(kind: str, raw: bytes, order: str) -> Any:
                    if kind == "ascii_string":
                        return raw.decode("ascii", errors="replace").rstrip(" " + chr(0))
                    if kind == "bytes":
                        return bytes(raw)
                    return struct.unpack((">" if order == "big" else "<") + _STRUCT[kind], raw)[0]


                class FieldNode(AbstractNode):
                    \"\"\"One decoded field.\"\"\"

                    def __init__(self, parent: DrbNode, name: str, value: Any, attributes: Dict[str, Any]):
                        super().__init__()
                        self.parent = parent
                        self.name = name
                        self.value = value
                        self._attrs = {(key, None): val for key, val in attributes.items()}

                    @property
                    def attributes(self) -> Dict[Tuple[str, str], Any]:
                        return self._attrs

                    @property
                    def children(self) -> List[DrbNode]:
                        return []

                    def __setitem__(self, key, value):
                        raise NotImplementedError

                    def __delitem__(self, key):
                        raise NotImplementedError


                class %sNode(WrappedNode):
                    \"\"\"A whole file decoded against FIELDS.\"\"\"

                    def __init__(self, base_node: DrbNode):
                        super().__init__(base_node)
                        with base_node.get_impl(io.BufferedIOBase) as stream:
                            data = stream.read()
                        self._children = []
                        offset = 0
                        for name, kind, length, order, semantic_name, definition, units in FIELDS:
                            raw = data[offset:offset + length]
                            if len(raw) < length:
                                raise ValueError(
                                    f"file ends at byte {len(data)}, before field {name!r} "
                                    f"({length} bytes at offset {offset})")
                            self._children.append(FieldNode(self, name, _decode(kind, raw, order), {
                                "offset": offset, "length": length, "type": kind,
                                "semantic_name": semantic_name, "definition": definition, "units": units}))
                            offset += length
                        self.trailing_bytes = len(data) - offset

                    @property
                    def children(self) -> List[DrbNode]:
                        return self._children


                class %sFactory(DrbFactory):
                    def _create(self, node: DrbNode) -> DrbNode:
                        if isinstance(node, %sNode):
                            return node
                        try:
                            return %sNode(node)
                        except Exception as ex:
                            raise DrbFactoryException(str(ex)) from ex
                """.formatted(quotedLiteral(def.getName()), quotedLiteral(def.getNotes()), fieldTable,
                className, className, className, className);
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

                This documents the EXPECTED group/dataset/attribute schema and field
                semantics for this kind of file, as a semantic reference alongside the
                archive's own SemanticRepresentationInformation entry. It is not a driver:
                no HDF5 driver for drb-python is published on PyPI, so reading an actual
                .h5 file through drb-python would need one written (e.g. wrapping h5py),
                exposing this tree as DrbNodes (.name, .value, .attributes, children).
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
        if (node.definition() != null && !node.definition().isBlank()) {
            row.append(", \"description\": \"%s\"".formatted(pyEscape(node.definition())));
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
            fieldDocs.append(" *   - %s: %s\n".formatted(field.name(), javaDocLine(field.definition())));
            fieldConstants.append("    /** %s */\n    public static final String %s = \"%s\";\n"
                    .formatted(javaDocLine(field.definition()), javaName, javaStringEscape(field.name())));
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
                    javaDocLine(node.definition())));
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

    /**
     * A double-quoted string literal valid in Python, TOML and Turtle alike --
     * all three accept the same {@code \\} escapes used here -- with every
     * control or non-ASCII character written as a {@code \\u}/{@code \\U}
     * escape, so the generated files are plain ASCII whatever was typed.
     */
    public static String quotedLiteral(String s) {
        StringBuilder sb = new StringBuilder("\"");
        (s == null ? "" : s).codePoints().forEach(cp -> {
            switch (cp) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (cp >= 0x20 && cp < 0x7f) {
                        sb.append((char) cp);
                    } else if (cp <= 0xffff) {
                        sb.append(String.format("\\u%04x", cp));
                    } else {
                        sb.append(String.format("\\U%08x", cp));
                    }
                }
            }
        });
        return sb.append('"').toString();
    }

    private String pyOptional(String s) {
        return s == null || s.isBlank() ? "None" : quotedLiteral(s);
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
