package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.Feature;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Scope;
import info.oais.infomodel.structure.description.Semantics;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a DRB description for either of the two real, unrelated DRB
 * implementations (see {@link DrbTarget}'s doc comment): {@code drb-python}
 * (https://gitlab.com/drb-python), or GAEL's Java DRB ({@code fr.gael.drb},
 * LGPL v3), which does have a declarative schema language: for a byte layout
 * the Java target is a DRB SDF schema ({@link #generate} with
 * {@link DrbTarget#JAVA}), applied by DRB itself through
 * {@code oais-structure-drb} -- see {@code DrbSampleRunner}.
 *
 * <p>drb-python has no declarative schema language: a format is supported by
 * a driver package (a {@code DrbFactory} plus {@code DrbNode}s, registered
 * through entry points). For a byte-layout definition this therefore
 * generates a real, installable driver ({@link #pythonDriverPackage}) that
 * decodes the fields; {@code DrbPythonSampleRunner} and
 * {@code DrbPythonSampleRunnerTest} run it against actual bytes. The package
 * also registers three drb-python add-ons (the {@code drb.addon} entry
 * point) giving a decoded file's semantics, metadata and checks, and can
 * carry a hand-written Python add-in ({@link FormatDefinition#handWritten}
 * for {@link DescriptionLanguage#DRB_PYTHON}) with hooks the driver calls --
 * see {@code drb-python/interpreter.py}. A logical-tree definition gets a
 * documented schema only.
 */
@Component
public class DrbGenerator {

    public String generate(FormatDefinition def, DrbTarget target) {
        boolean byteLayout = def.getKind() == FormatDefinitionKind.BYTE_LAYOUT;
        return switch (target) {
            case PYTHON -> byteLayout ? generatePythonByteLayout(def) : generatePythonLogicalTree(def);
            case JAVA -> byteLayout ? generateSdfSchema(def) : generateJavaLogicalTree(def);
        };
    }

    /**
     * The driver package's import path under drb-python's {@code drb.drivers}/
     * {@code drb.topics} namespaces, and its entry-point name -- prefixed
     * {@code am_} so a generated driver can't collide with an official one
     * (e.g. {@code drb.drivers.csv}).
     */
    public String pythonDriverId(FormatDefinition def) {
        return driverId(def.getName());
    }

    static String driverId(String formatName) {
        // Capped because the id appears twice in pip's build paths, and a long
        // format name can otherwise break an install on Windows' 260-character path limit.
        String snake = FormatIdentifiers.snakeCase(formatName);
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

    /** The names of the add-ons every generated driver package registers, after its driver id. */
    public static final List<String> ADDON_KINDS = List.of("semantics", "metadata", "checks");

    /**
     * A complete, pip-installable drb-python driver package for a byte-layout
     * definition, as {@code relative path -> file content}: the driver module
     * itself ({@link #generate} with {@link DrbTarget#PYTHON}), a drb "topic"
     * ({@code cortex.ttl}) whose signature matches the definition's
     * {@link FormatDefinition#getFileExtensions() file extensions}, and a
     * {@code pyproject.toml} registering them through drb's {@code drb.driver}
     * and {@code drb.topic} entry points -- the same layout as drb-python's own
     * published drivers (e.g. {@code drb-driver-csv}) -- and the driver's
     * add-ons through {@code drb.addon}. The hand-written add-in, if there is
     * one, is the driver's {@code addin.py}. {@code null} for a logical-tree
     * definition, which has no byte layout to decode.
     */
    public Map<String, String> pythonDriverPackage(FormatDefinition def) {
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return null;
        }
        String id = pythonDriverId(def);
        String className = toPascalCase(def.getName());
        String addIn = def.handWritten(DescriptionLanguage.DRB_PYTHON).orElse(null);
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

                [project.entry-points."drb.addon"]
                %s

                [tool.setuptools]
                packages = ["drb.drivers.%s", "drb.topics.%s"]

                [tool.setuptools.package-data]
                "drb.topics.%s" = ["cortex.ttl"]
                """.formatted(pythonDistributionName(def),
                quotedLiteral("drb-python driver for " + def.getName() + " (generated by archive-manager)"),
                id, id, className, id, id,
                String.join("\n", ADDON_KINDS.stream().map(k -> "%s_%s = \"drb.drivers.%s:%sAddon\""
                        .formatted(id, k, id, Character.toUpperCase(k.charAt(0)) + k.substring(1))).toList()),
                id, id, id));
        files.put("drb/drivers/" + id + "/__init__.py", generatePythonByteLayout(def));
        if (addIn != null) {
            files.put("drb/drivers/" + id + "/addin.py", addIn);
        }
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

                ## Add-ons

                The package registers three drb-python add-ons for this format, which
                give a decoded file's meaning as a whole:

                    node.get_impl(list, "%s_semantics")  # every value with its meaning
                    node.get_impl(dict, "%s_metadata")   # semantic name -> value
                    node.get_impl(list, "%s_checks")     # problems found; empty if none

                `semantics` lists every value with its semantic name, definition, units,
                a code's meaning and a scaled value's physical value. `metadata` gives each
                value by its semantic name (or element path) as meant: a code's meaning, a
                physical value, or the value itself. `checks` reports values outside their
                valid range or code list, and bytes the description doesn't cover.
                %s""".formatted(pythonDistributionName(def), def.getName(), pythonDistributionName(def),
                def.getFileExtensions().isEmpty()
                        ? "No file extensions were given, so drb won't pick this driver automatically.\n"
                        : """
                        drb then selects this driver automatically for files ending in %s:

                            import drb.topics.resolver as resolver
                            node = resolver.create("path/to/file.%s")
                        """.formatted(String.join(", ", def.getFileExtensions().stream().map(e -> "." + e).toList()),
                        def.getFileExtensions().get(0)),
                id, className, className, id, id, id,
                addIn == null ? "" : """

                ## Hand-written add-in

                `drb/drivers/%s/addin.py` was written by hand. The driver calls its hooks:
                `prepare(data)` on the file's bytes before decoding (e.g. to decrypt or
                decompress them), `check(root)` after decoding (its problems are added to
                `checks`), and `metadata(root)` (merged into `metadata`). It is Python code
                that runs whenever the driver is used, so read it before installing.
                """.formatted(id)));
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
                """.formatted(id, topicId(id), quotedLiteral(def.getName()), signature, id);
    }

    /** The driver's drb topic id, also how its add-ons recognise the files they apply to. */
    static UUID topicId(String driverId) {
        return UUID.nameUUIDFromBytes(("archive-manager:drb-python:" + driverId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The drb-python driver module: the generic interpreter
     * ({@code drb-python/interpreter.py}, the same for every format), then the
     * format's description as a Python data literal ({@link #pythonDescription}),
     * then the {@code DrbFactory} drb-python registers. Nothing from the
     * description is written as Python code: names are validated identifiers,
     * text is an escaped string literal, and expressions are data trees the
     * interpreter evaluates.
     */
    private String generatePythonByteLayout(FormatDefinition def) {
        return pythonModule(def.toFormatDescription(), pythonFactoryClassName(def));
    }

    /** The drb-python driver module for {@code format}, defining {@code factoryClassName} and its add-ons. */
    public String pythonModule(FormatDescription format, String factoryClassName) {
        Feature.requireSupported(format, DescriptionLanguage.DRB_PYTHON);
        String id = driverId(format.name());
        return INTERPRETER + """

                DESCRIPTION = %s

                ADDON_PREFIX = %s
                TOPIC_ID = %s


                class %s(DrbFactory):
                    def _create(self, node: DrbNode) -> DrbNode:
                        if isinstance(node, _FormatNode):
                            return node
                        try:
                            return _FormatNode(node)
                        except Exception as ex:
                            raise DrbFactoryException(str(ex)) from ex
                """.formatted(pythonDescription(format), quotedLiteral(id), quotedLiteral(topicId(id).toString()),
                factoryClassName);
    }

    private static final String INTERPRETER = loadInterpreter();

    private static String loadInterpreter() {
        try (var in = DrbGenerator.class.getResourceAsStream("/drb-python/interpreter.py")) {
            if (in == null) {
                throw new IllegalStateException("drb-python/interpreter.py is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The description as the Python data literal {@code interpreter.py} walks. */
    static String pythonDescription(FormatDescription format) {
        return "{\"name\": " + quotedLiteral(format.name()) + ", \"order\": "
                + (format.defaultByteOrder() == ByteOrder.LITTLE_ENDIAN ? "\"little\"" : "\"big\"")
                + ", \"root\": " + pyElement(format.root()) + "}";
    }

    private static String pyElement(ElementDescription e) {
        StringBuilder sb = new StringBuilder("{\"kind\": ");
        if (e instanceof FieldDescription f) {
            sb.append("\"field\", \"name\": ").append(quotedLiteral(f.name()))
                    .append(", \"type\": \"").append(f.type().name().toLowerCase()).append('"')
                    .append(", \"length\": ").append(f.length() == null ? "None" : pyExpr(f.length()))
                    .append(", \"order\": ").append(f.byteOrder() == null ? "None"
                            : f.byteOrder() == ByteOrder.LITTLE_ENDIAN ? "\"little\"" : "\"big\"");
        } else if (e instanceof RecordDescription r) {
            sb.append("\"record\", \"name\": ").append(quotedLiteral(r.name())).append(", \"children\": [");
            sb.append(String.join(", ", r.children().stream().map(DrbGenerator::pyElement).toList())).append(']');
            sb.append(", \"text\": ").append(r.isText()
                    ? "[" + quotedLiteral(r.text().fieldSeparator()) + ", " + quotedLiteral(r.text().recordTerminator()) + "]"
                    : "None");
        } else {
            ChoiceDescription c = (ChoiceDescription) e;
            sb.append("\"choice\", \"name\": ").append(quotedLiteral(c.name()))
                    .append(", \"on\": ").append(pyExpr(c.discriminator())).append(", \"branches\": [");
            sb.append(String.join(", ", c.branches().stream().map(b -> "(" + (b.isIntegerKey()
                    ? Long.toString(Long.parseLong(b.key().strip())) : quotedLiteral(b.key())) + ", "
                    + pyElement(b.record()) + ")").toList())).append(']');
        }
        sb.append(", \"occurs\": ").append(pyOccurrence(e.occurrence()));
        sb.append(", \"sem\": ").append(pySemantics(e.semantics())).append('}');
        return sb.toString();
    }

    private static String pyOccurrence(Occurrence o) {
        if (o instanceof Occurrence.Optional opt) {
            return "[\"optional\", " + pyExpr(opt.condition()) + "]";
        }
        if (o instanceof Occurrence.Repeated r) {
            return "[\"repeat\", " + pyExpr(r.count()) + "]";
        }
        return o instanceof Occurrence.UntilEnd ? "[\"until_end\"]" : "None";
    }

    /** An expression as a data tree for the interpreter's {@code _eval}, never as Python code. */
    private static String pyExpr(Expression e) {
        if (e instanceof Expression.IntLiteral lit) {
            return "[\"int\", " + lit.value() + "]";
        }
        if (e instanceof Expression.StringLiteral lit) {
            return "[\"str\", " + quotedLiteral(lit.value()) + "]";
        }
        if (e instanceof Expression.FieldRef ref) {
            return "[\"ref\", " + quotedLiteral(ref.name()) + "]";
        }
        if (e instanceof Expression.Unary u) {
            return "[\"" + (u.op() == Expression.Op.NOT ? "not" : "neg") + "\", " + pyExpr(u.operand()) + "]";
        }
        Expression.Binary b = (Expression.Binary) e;
        String op = switch (b.op()) {
            case OR -> "or";
            case AND -> "and";
            case EQ -> "=";
            case NE -> "!=";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            default -> throw new IllegalArgumentException(b.op().toString());
        };
        return "[\"op\", \"" + op + "\", " + pyExpr(b.left()) + ", " + pyExpr(b.right()) + "]";
    }

    private static String pySemantics(Semantics s) {
        if (s.isEmpty()) {
            return "None";
        }
        List<String> entries = new ArrayList<>();
        addEntry(entries, "semantic_name", s.semanticName());
        addEntry(entries, "definition", s.definition());
        addEntry(entries, "units", s.units());
        addEntry(entries, "units_uri", s.unitsUri() == null ? null : s.unitsUri().toString());
        addEntry(entries, "concept_uri", s.conceptUri() == null ? null : s.conceptUri().toString());
        addEntry(entries, "scale", s.scale() == null ? null : s.scale().toPlainString());
        addEntry(entries, "offset", s.offset() == null ? null : s.offset().toPlainString());
        addEntry(entries, "fill", s.fillValue());
        addEntry(entries, "min", s.validMin() == null ? null : s.validMin().toPlainString());
        addEntry(entries, "max", s.validMax() == null ? null : s.validMax().toPlainString());
        if (!s.codes().isEmpty()) {
            entries.add("\"codes\": {" + String.join(", ", s.codes().entrySet().stream()
                    .map(c -> quotedLiteral(c.getKey()) + ": " + quotedLiteral(c.getValue())).toList()) + "}");
        }
        return "{" + String.join(", ", entries) + "}";
    }

    private static void addEntry(List<String> entries, String key, String value) {
        if (value != null) {
            entries.add("\"" + key + "\": " + quotedLiteral(value));
        }
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
     * Java DRB target, byte layout: a DRB SDF (Structured Data File) schema --
     * an XML Schema whose elements carry {@code sdf:block} annotations, the
     * same form as GAEL's own examples (e.g. an {@code mmm.xsd}) -- which DRB
     * itself applies to the bytes, through {@code oais-structure-drb} (see
     * {@code DrbSampleRunner}).
     *
     * <ul>
     *   <li>Fields give {@code sdf:length} (a literal, or a {@code query} for a
     *       computed length), {@code sdf:byteOrder} and {@code sdf:encoding}.</li>
     *   <li>Repetition is {@code maxOccurs} plus {@code sdf:occurrence} (a
     *       {@code query} for a computed count); an optional element is an
     *       occurrence query of 0 or 1.</li>
     *   <li>A choice is an element holding an {@code xs:choice}; DRB picks the
     *       first branch whose {@code sdf:signature} query is true.</li>
     *   <li>A delimited-text record's fields end at an {@code sdf:delimiter}.</li>
     *   <li>Every query carries {@code constant="false"}: DRB otherwise
     *       evaluates a query once and reuses the result for every repeated
     *       record, which is wrong whenever it depends on that record's fields.</li>
     * </ul>
     *
     * <p>DRB has no raw-bytes type ({@code xs:hexBinary} decodes as nothing),
     * so a {@code BYTES} field becomes a run of {@code xs:unsignedByte}
     * occurrences. Each element's semantics become its {@code xs:documentation},
     * which DRB reports back as the decoded node's {@code documentation} attribute.
     */
    private String generateSdfSchema(FormatDefinition def) {
        return generateSdfSchema(def.toFormatDescription());
    }

    /** The DRB SDF schema for {@code format} (see the {@code FormatDefinition} overload for what it contains). */
    public String generateSdfSchema(FormatDescription format) {
        Feature.requireSupported(format, DescriptionLanguage.DRB);
        Scope scope = new Scope(format);
        StringBuilder root = new StringBuilder();
        sdfRecord(root, format.root(), "  ", format, scope, false, null);
        String notes = format.notes().isBlank() ? "" : """

                  <xs:annotation>
                    <xs:documentation>%s</xs:documentation>
                  </xs:annotation>
                """.formatted(xmlEscape(format.notes()));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!--
                  DRB SDF schema for "%s", generated by archive-manager's RepInfo Tools.
                  Apply it with GAEL's DRB (fr.gael.drb, 2.5): e.g. the XQuery
                  doc("data-file")/(this-schema.xsd)%s, or oais-structure-drb's
                  new DrbFormatSpecification(schemaUri).
                -->
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:sdf="http://www.gael.fr/2004/12/drb/sdf">
                %s
                %s</xs:schema>
                """.formatted(xmlCommentText(format.name()), format.root().name(), notes, root);
    }

    private void sdfRecord(StringBuilder sb, RecordDescription r, String in, FormatDescription format, Scope scope,
                           boolean withOccurrence, String signature) {
        String block = (withOccurrence ? sdfOccurrence(r, scope) : "")
                + (signature == null ? "" : "<sdf:signature query=\"" + xmlEscape(signature) + "\"/>");
        sb.append(in).append("<xs:element name=\"").append(r.name()).append('"')
                .append(withOccurrence ? sdfMaxOccurs(r) : "").append(">\n");
        sdfAnnotation(sb, r.semantics(), block, in + "  ");
        sb.append(in).append("  <xs:complexType>\n");
        sb.append(in).append("    <xs:sequence>\n");
        List<ElementDescription> children = r.children();
        for (int i = 0; i < children.size(); i++) {
            ElementDescription child = children.get(i);
            String cin = in + "      ";
            if (child instanceof RecordDescription cr) {
                sdfRecord(sb, cr, cin, format, scope, true, null);
            } else if (child instanceof ChoiceDescription c) {
                sdfChoice(sb, c, cin, format, scope);
            } else {
                String delimiter = r.isText()
                        ? (i == children.size() - 1 ? r.text().recordTerminator() : r.text().fieldSeparator()) : null;
                sdfField(sb, (FieldDescription) child, delimiter, cin, format, scope);
            }
        }
        sb.append(in).append("    </xs:sequence>\n");
        sb.append(in).append("  </xs:complexType>\n");
        sb.append(in).append("</xs:element>\n");
    }

    private void sdfChoice(StringBuilder sb, ChoiceDescription c, String in, FormatDescription format, Scope scope) {
        sb.append(in).append("<xs:element name=\"").append(c.name()).append('"').append(sdfMaxOccurs(c)).append(">\n");
        sdfAnnotation(sb, c.semantics(), sdfOccurrence(c, scope), in + "  ");
        sb.append(in).append("  <xs:complexType>\n");
        sb.append(in).append("    <xs:choice>\n");
        for (ChoiceDescription.Branch b : c.branches()) {
            // The signature is evaluated on the branch's own node, one level below the choice.
            Expression test = new Expression.Binary(Expression.Op.EQ, c.discriminator(), b.isIntegerKey()
                    ? new Expression.IntLiteral(Long.parseLong(b.key().strip())) : new Expression.StringLiteral(b.key()));
            String signature = test.render(EngineSyntax.drb(scope, c.id(), 1));
            sdfRecord(sb, b.record(), in + "      ", format, scope, false, signature);
        }
        sb.append(in).append("    </xs:choice>\n");
        sb.append(in).append("  </xs:complexType>\n");
        sb.append(in).append("</xs:element>\n");
    }

    private void sdfField(StringBuilder sb, FieldDescription f, String delimiter, String in,
                          FormatDescription format, Scope scope) {
        String xsType;
        StringBuilder block = new StringBuilder();
        String maxOccurs = sdfMaxOccurs(f);
        if (delimiter != null) {
            xsType = f.type() == PrimitiveType.STRING ? "xs:string" : sdfNumericType(f.type());
            block.append("<sdf:encoding>ASCII</sdf:encoding><sdf:delimiter>").append(xmlEscapeAll(delimiter))
                    .append("</sdf:delimiter>");
        } else if (f.type() == PrimitiveType.STRING) {
            xsType = "xs:string";
            block.append(sdfLength(f, scope)).append("<sdf:encoding>ASCII</sdf:encoding>");
        } else if (f.type() == PrimitiveType.BYTES) {
            // DRB can't decode raw bytes; describe them as a run of unsigned bytes.
            xsType = "xs:unsignedByte";
            maxOccurs = " maxOccurs=\"unbounded\"";
            String count = f.length() instanceof Expression.IntLiteral lit ? Long.toString(lit.value()) : null;
            block.append("<sdf:length>1</sdf:length>");
            block.append(count != null ? "<sdf:occurrence>" + count + "</sdf:occurrence>"
                    : "<sdf:occurrence query=\"" + xmlEscape(f.length().render(EngineSyntax.drb(scope, f.id(), 0)))
                    + "\" constant=\"false\"/>");
        } else {
            xsType = sdfNumericType(f.type());
            block.append("<sdf:length>").append(f.type().fixedWidth()).append("</sdf:length>");
            if (f.type().fixedWidth() > 1) {
                ByteOrder order = f.byteOrder() == null ? format.defaultByteOrder() : f.byteOrder();
                block.append("<sdf:byteOrder>").append(order == ByteOrder.LITTLE_ENDIAN ? "LSB" : "MSB")
                        .append("</sdf:byteOrder>");
            }
        }
        if (f.type() != PrimitiveType.BYTES) {
            block.append(sdfOccurrence(f, scope));
        }
        sb.append(in).append("<xs:element name=\"").append(f.name()).append("\" type=\"").append(xsType).append('"')
                .append(maxOccurs).append(">\n");
        sdfAnnotation(sb, f.semantics(), block.toString(), in + "  ");
        sb.append(in).append("</xs:element>\n");
    }

    private String sdfLength(FieldDescription f, Scope scope) {
        if (f.length() instanceof Expression.IntLiteral lit) {
            return "<sdf:length>" + lit.value() + "</sdf:length>";
        }
        return "<sdf:length query=\"" + xmlEscape(f.length().render(EngineSyntax.drb(scope, f.id(), 0)))
                + "\" constant=\"false\"/>";
    }

    private String sdfOccurrence(ElementDescription e, Scope scope) {
        if (e.occurrence() instanceof Occurrence.Repeated r) {
            if (r.count() instanceof Expression.IntLiteral lit) {
                return "<sdf:occurrence>" + lit.value() + "</sdf:occurrence>";
            }
            return "<sdf:occurrence query=\"" + xmlEscape(r.count().render(EngineSyntax.drb(scope, e.id(), 0)))
                    + "\" constant=\"false\"/>";
        }
        if (e.occurrence() instanceof Occurrence.Optional o) {
            return "<sdf:occurrence query=\"" + xmlEscape("if (" + o.condition().render(EngineSyntax.drb(scope, e.id(), 0))
                    + ") then 1 else 0") + "\" constant=\"false\"/>";
        }
        return "";
    }

    private static String sdfMaxOccurs(ElementDescription e) {
        if (e.occurrence().isRepeated()) {
            return " minOccurs=\"0\" maxOccurs=\"unbounded\"";
        }
        return e.occurrence() instanceof Occurrence.Optional ? " minOccurs=\"0\" maxOccurs=\"1\"" : "";
    }

    private void sdfAnnotation(StringBuilder sb, Semantics s, String block, String in) {
        String doc = KaitaiGenerator.describe(s);
        if (doc.isEmpty() && block.isEmpty()) {
            return;
        }
        sb.append(in).append("<xs:annotation>\n");
        if (!doc.isEmpty()) {
            sb.append(in).append("  <xs:documentation>").append(xmlEscape(doc)).append("</xs:documentation>\n");
        }
        if (!block.isEmpty()) {
            sb.append(in).append("  <xs:appinfo><sdf:block>").append(block).append("</sdf:block></xs:appinfo>\n");
        }
        sb.append(in).append("</xs:annotation>\n");
    }

    private static String sdfNumericType(PrimitiveType type) {
        return switch (type) {
            case INT8 -> "xs:byte";
            case UINT8 -> "xs:unsignedByte";
            case INT16 -> "xs:short";
            case UINT16 -> "xs:unsignedShort";
            case INT32 -> "xs:int";
            case UINT32 -> "xs:unsignedInt";
            case INT64 -> "xs:long";
            case UINT64 -> "xs:unsignedLong";
            case FLOAT32 -> "xs:float";
            default -> "xs:double";
        };
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Like {@link #xmlEscape}, plus control characters as character references (e.g. a newline delimiter). */
    private static String xmlEscapeAll(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 0x20 ? "&#" + (int) c + ";" : xmlEscape(String.valueOf(c)));
        }
        return sb.toString();
    }

    /** Text safe inside an XML comment, which can't contain "--". */
    private static String xmlCommentText(String s) {
        return s.replace("--", "- -").replaceAll("-$", "- ");
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
                 * This documents the EXPECTED group/dataset/attribute schema and field
                 * semantics for this kind of file, for reference alongside the archive's own
                 * SemanticRepresentationInformation entry. It is not something DRB reads:
                 * DRB 2.5 has no HDF5 implementation, and its SDF schemas describe sequential
                 * byte layouts, not a self-describing container's internal tree.
                 *
                %s *
                 * Adjust the package above to fit your project.
                 */
                public final class %s {

                    private %s() {
                    }
                }
                """.formatted(javaDocLine(def.getName()), schemaDocs, className, className);
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

}
