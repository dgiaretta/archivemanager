package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.Descriptions;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a Kaitai Struct {@code .ksy} definition (https://doc.kaitai.io/ksy_reference.html)
 * from a byte-layout definition's {@link FormatDescription}. Each record and
 * choice branch becomes a Kaitai type; a choice becomes a {@code switch-on}
 * field; repetition becomes {@code repeat: expr}/{@code repeat: eos}; an
 * optional element becomes {@code if:}; a delimited-text record's fields
 * become {@code str} fields ending at their separator (Kaitai keeps numbers in
 * text as text). Each element's semantics go into its {@code doc}.
 *
 * <p>Hand-built text, not a generic YAML serializer: Kaitai's own
 * {@code meta}/{@code seq}/{@code types} idiom is kept exactly, and every
 * user-entered string is written as a single-quoted YAML scalar.
 */
@Component
public class KaitaiGenerator {

    /** @return the generated {@code .ksy} YAML text, or {@code null} if {@code def} isn't byte-layout. */
    public String generate(FormatDefinition def) {
        return def.getKind() == FormatDefinitionKind.BYTE_LAYOUT ? generate(def.toFormatDescription()) : null;
    }

    public String generate(FormatDescription format) {
        Scope scope = new Scope(format);
        Map<String, String> typeNames = typeNames(format.root());
        StringBuilder sb = new StringBuilder();
        sb.append("meta:\n");
        sb.append("  id: ").append(format.root().name()).append('\n');
        sb.append("  endian: ").append(format.defaultByteOrder() == ByteOrder.LITTLE_ENDIAN ? "le" : "be").append('\n');
        sb.append("  encoding: ASCII\n");
        if (!format.notes().isBlank()) {
            sb.append("doc: ").append(yamlQuote(format.notes())).append('\n');
        }
        appendSeq(sb, format.root(), "", format, scope, typeNames);

        List<ElementDescription> types = Descriptions.all(format.root()).stream()
                .filter(e -> e instanceof RecordDescription && e != format.root()).toList();
        if (!types.isEmpty()) {
            sb.append("types:\n");
            for (ElementDescription t : types) {
                RecordDescription r = (RecordDescription) t;
                sb.append("  ").append(typeNames.get(r.id())).append(":\n");
                appendDoc(sb, r.semantics(), "    ");
                appendSeq(sb, r, "    ", format, scope, typeNames);
            }
        }
        return sb.toString();
    }

    private void appendSeq(StringBuilder sb, RecordDescription record, String indent, FormatDescription format,
                           Scope scope, Map<String, String> typeNames) {
        sb.append(indent).append("seq:\n");
        List<ElementDescription> children = record.children();
        for (int i = 0; i < children.size(); i++) {
            ElementDescription e = children.get(i);
            String in = indent + "    ";
            sb.append(indent).append("  - id: ").append(e.name()).append('\n');
            if (e instanceof FieldDescription f) {
                if (record.isText()) {
                    boolean last = i == children.size() - 1;
                    String delimiter = last ? record.text().recordTerminator() : record.text().fieldSeparator();
                    sb.append(in).append("type: str\n");
                    sb.append(in).append("terminator: ").append((int) delimiter.charAt(0)).append('\n');
                    if (last) {
                        // A last line with no newline still ends the last field.
                        sb.append(in).append("eos-error: false\n");
                    }
                } else {
                    appendFieldType(sb, f, in, format.defaultByteOrder(), scope);
                }
            } else if (e instanceof RecordDescription r) {
                sb.append(in).append("type: ").append(typeNames.get(r.id())).append('\n');
            } else if (e instanceof ChoiceDescription c) {
                sb.append(in).append("type:\n");
                sb.append(in).append("  switch-on: ")
                        .append(yamlQuote(c.discriminator().render(EngineSyntax.kaitai(scope, c.id())))).append('\n');
                sb.append(in).append("  cases:\n");
                for (ChoiceDescription.Branch b : c.branches()) {
                    String key = b.isIntegerKey() ? b.key().strip()
                            : yamlQuote("\"" + b.key().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
                    sb.append(in).append("    ").append(key).append(": ").append(typeNames.get(b.record().id())).append('\n');
                }
            }
            appendOccurrence(sb, e, in, scope);
            appendDoc(sb, e.semantics(), in);
        }
    }

    private void appendFieldType(StringBuilder sb, FieldDescription f, String in, ByteOrder defaultOrder, Scope scope) {
        String suffix = "";
        if (f.type().fixedWidth() > 1 && f.byteOrder() != null && f.byteOrder() != defaultOrder) {
            suffix = f.byteOrder() == ByteOrder.LITTLE_ENDIAN ? "le" : "be";
        }
        switch (f.type()) {
            case INT8 -> sb.append(in).append("type: s1\n");
            case UINT8 -> sb.append(in).append("type: u1\n");
            case INT16 -> sb.append(in).append("type: s2").append(suffix).append('\n');
            case UINT16 -> sb.append(in).append("type: u2").append(suffix).append('\n');
            case INT32 -> sb.append(in).append("type: s4").append(suffix).append('\n');
            case UINT32 -> sb.append(in).append("type: u4").append(suffix).append('\n');
            case INT64 -> sb.append(in).append("type: s8").append(suffix).append('\n');
            case UINT64 -> sb.append(in).append("type: u8").append(suffix).append('\n');
            case FLOAT32 -> sb.append(in).append("type: f4").append(suffix).append('\n');
            case FLOAT64 -> sb.append(in).append("type: f8").append(suffix).append('\n');
            case STRING -> {
                sb.append(in).append("type: str\n");
                sb.append(in).append("size: ").append(expr(f.length(), f, scope)).append('\n');
            }
            case BYTES -> sb.append(in).append("size: ").append(expr(f.length(), f, scope)).append('\n');
        }
    }

    private void appendOccurrence(StringBuilder sb, ElementDescription e, String in, Scope scope) {
        if (e.occurrence() instanceof Occurrence.Repeated r) {
            sb.append(in).append("repeat: expr\n");
            sb.append(in).append("repeat-expr: ").append(expr(r.count(), e, scope)).append('\n');
        } else if (e.occurrence() instanceof Occurrence.UntilEnd) {
            sb.append(in).append("repeat: eos\n");
        } else if (e.occurrence() instanceof Occurrence.Optional o) {
            sb.append(in).append("if: ").append(expr(o.condition(), e, scope)).append('\n');
        }
    }

    private String expr(Expression expression, ElementDescription from, Scope scope) {
        if (expression instanceof Expression.IntLiteral lit) {
            return Long.toString(lit.value());
        }
        return yamlQuote(expression.render(EngineSyntax.kaitai(scope, from.id())));
    }

    private void appendDoc(StringBuilder sb, Semantics s, String in) {
        String doc = describe(s);
        if (!doc.isEmpty()) {
            sb.append(in).append("doc: ").append(yamlQuote(doc)).append('\n');
        }
        if (s.conceptUri() != null) {
            sb.append(in).append("doc-ref: ").append(yamlQuote(s.conceptUri().toString())).append('\n');
        }
    }

    /** An element's semantics as one line of prose, for a {@code doc:}. */
    static String describe(Semantics s) {
        StringBuilder sb = new StringBuilder();
        if (s.semanticName() != null) {
            sb.append(s.semanticName());
            if (s.units() != null) {
                sb.append(" (").append(s.units()).append(")");
            }
            sb.append(s.definition() != null ? ": " : ".");
        } else if (s.units() != null) {
            sb.append("In ").append(s.units()).append(s.definition() != null ? ": " : ".");
        }
        if (s.definition() != null) {
            sb.append(s.definition());
        }
        if (s.isScaled()) {
            sb.append(sb.length() > 0 ? " " : "").append("Physical value = raw")
                    .append(s.scale() != null ? " * " + s.scale().toPlainString() : "")
                    .append(s.offset() != null ? " + " + s.offset().toPlainString() : "").append('.');
        }
        if (!s.codes().isEmpty()) {
            sb.append(sb.length() > 0 ? " " : "").append("Codes: ");
            s.codes().forEach((k, v) -> sb.append(k).append(" = ").append(v).append("; "));
            sb.setLength(sb.length() - 2);
            sb.append('.');
        }
        if (s.fillValue() != null) {
            sb.append(sb.length() > 0 ? " " : "").append("Fill value (no data): ").append(s.fillValue()).append('.');
        }
        if (s.validMin() != null || s.validMax() != null) {
            sb.append(sb.length() > 0 ? " " : "").append("Valid range: ")
                    .append(s.validMin() != null ? s.validMin().toPlainString() : "")
                    .append(" to ").append(s.validMax() != null ? s.validMax().toPlainString() : "").append('.');
        }
        return sb.toString();
    }

    /** A Kaitai type name per record, unique across the whole file (records in different places may share a name). */
    private static Map<String, String> typeNames(RecordDescription root) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        used.add(root.name());
        for (ElementDescription e : Descriptions.all(root)) {
            if (e instanceof RecordDescription r && r != root) {
                String name = r.name();
                for (int i = 2; !used.add(name); i++) {
                    name = r.name() + "_" + i;
                }
                names.put(r.id(), name);
            }
        }
        return names;
    }

    /** A YAML single-quoted scalar: only {@code '} itself needs escaping (doubled). */
    private static String yamlQuote(String s) {
        return "'" + s.replace("'", "''").replace("\r", " ").replace("\n", " ") + "'";
    }
}
