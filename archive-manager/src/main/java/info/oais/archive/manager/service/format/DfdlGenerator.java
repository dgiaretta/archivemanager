package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Scope;
import info.oais.infomodel.structure.description.Semantics;
import org.springframework.stereotype.Component;

/**
 * Generates a DFDL schema (Data Format Description Language v1.0, OGF
 * GFD.207) from a byte-layout definition's {@link FormatDescription}: an
 * XML Schema whose elements carry {@code dfdl:} annotations. Records become
 * complex elements, a choice becomes an element holding an {@code xs:choice}
 * dispatched on its discriminator, repetition and optional elements use
 * {@code dfdl:occursCount} expressions, and a delimited-text record uses
 * {@code dfdl:separator}/{@code dfdl:terminator}. Each element's semantics
 * go into its {@code xs:documentation}.
 *
 * <p>Includes Daffodil's built-in {@code GeneralFormat} (resolved from
 * daffodil-lib's jar) rather than listing every DFDL property by hand:
 * several low-level properties ({@code leadingSkip}, {@code initiatedContent},
 * {@code textBidi}, ...) have no default, and Daffodil refuses to compile a
 * schema that leaves any of them unset. {@code DfdlSampleRunnerTest} and
 * {@code GeneratedDescriptionsMatrixTest} compile this output with a real
 * Daffodil to keep that honest.
 */
@Component
public class DfdlGenerator {

    /** @return the generated {@code .dfdl.xsd} XML text, or {@code null} if {@code def} isn't byte-layout. */
    public String generate(FormatDefinition def) {
        return def.getKind() == FormatDefinitionKind.BYTE_LAYOUT ? generate(def.toFormatDescription()) : null;
    }

    public String generate(FormatDescription format) {
        Scope scope = new Scope(format);
        String namespace = "urn:archive-manager:format:" + format.root().name();
        StringBuilder root = new StringBuilder();
        appendRecordElement(root, format.root(), "  ", format, scope, true);
        String notes = format.notes().isBlank() ? "" : """

                  <xs:annotation>
                    <xs:documentation>%s</xs:documentation>
                  </xs:annotation>
                """.formatted(xmlEscape(format.notes()));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:dfdl="http://www.ogf.org/dfdl/dfdl-1.0/"
                           xmlns:fn="http://www.w3.org/2005/xpath-functions"
                           targetNamespace="%s"
                           xmlns:tns="%s"
                           elementFormDefault="qualified">

                  <xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>

                  <xs:annotation>
                    <xs:appinfo source="http://www.ogf.org/dfdl/">
                      <dfdl:defineFormat name="binaryDefaults">
                        <dfdl:format ref="tns:GeneralFormat" representation="binary" byteOrder="%s"
                                     binaryNumberRep="binary" lengthUnits="bytes" lengthKind="explicit"
                                     encoding="US-ASCII" occursCountKind="implicit"
                                     documentFinalTerminatorCanBeMissing="yes"/>
                      </dfdl:defineFormat>
                      <dfdl:format ref="tns:binaryDefaults"/>
                    </xs:appinfo>
                  </xs:annotation>
                %s
                %s</xs:schema>
                """.formatted(namespace, namespace,
                format.defaultByteOrder() == ByteOrder.LITTLE_ENDIAN ? "littleEndian" : "bigEndian", notes, root);
    }

    private void appendRecordElement(StringBuilder sb, RecordDescription r, String in, FormatDescription format,
                                     Scope scope, boolean isRoot) {
        sb.append(in).append("<xs:element name=\"").append(r.name()).append("\" dfdl:lengthKind=\"implicit\"");
        if (r.isText()) {
            sb.append(" dfdl:representation=\"text\" dfdl:terminator=\"")
                    .append(dfdlLiteral(r.text().recordTerminator(), true)).append('"');
        }
        if (!isRoot) {
            appendOccurrence(sb, r, scope);
        }
        sb.append(">\n");
        appendDoc(sb, r.semantics(), in + "  ");
        sb.append(in).append("  <xs:complexType>\n");
        sb.append(in).append("    <xs:sequence");
        if (r.isText()) {
            sb.append(" dfdl:separator=\"").append(dfdlLiteral(r.text().fieldSeparator(), false))
                    .append("\" dfdl:separatorPosition=\"infix\"");
        }
        sb.append(">\n");
        for (ElementDescription child : r.children()) {
            appendElement(sb, child, r, in + "      ", format, scope);
        }
        sb.append(in).append("    </xs:sequence>\n");
        sb.append(in).append("  </xs:complexType>\n");
        sb.append(in).append("</xs:element>\n");
    }

    private void appendElement(StringBuilder sb, ElementDescription e, RecordDescription parent, String in,
                               FormatDescription format, Scope scope) {
        if (e instanceof RecordDescription r) {
            appendRecordElement(sb, r, in, format, scope, false);
        } else if (e instanceof ChoiceDescription c) {
            appendChoice(sb, c, in, format, scope);
        } else {
            appendField(sb, (FieldDescription) e, parent.isText(), in, format, scope);
        }
    }

    private void appendChoice(StringBuilder sb, ChoiceDescription c, String in, FormatDescription format, Scope scope) {
        sb.append(in).append("<xs:element name=\"").append(c.name()).append("\" dfdl:lengthKind=\"implicit\"");
        appendOccurrence(sb, c, scope);
        sb.append(">\n");
        appendDoc(sb, c.semantics(), in + "  ");
        sb.append(in).append("  <xs:complexType>\n");
        sb.append(in).append("    <xs:choice dfdl:choiceDispatchKey=\"{ xs:string(")
                .append(xmlEscape(c.discriminator().render(EngineSyntax.dfdl(scope, c.id(), 0)))).append(") }\">\n");
        for (ChoiceDescription.Branch b : c.branches()) {
            StringBuilder branch = new StringBuilder();
            appendRecordElement(branch, b.record(), in + "      ", format, scope, true);
            // Mark the branch element with the key that selects it.
            int close = branch.indexOf(">");
            branch.insert(close, " dfdl:choiceBranchKey=\"" + xmlEscape(b.key().strip()) + "\"");
            sb.append(branch);
        }
        sb.append(in).append("    </xs:choice>\n");
        sb.append(in).append("  </xs:complexType>\n");
        sb.append(in).append("</xs:element>\n");
    }

    private void appendField(StringBuilder sb, FieldDescription f, boolean inText, String in,
                             FormatDescription format, Scope scope) {
        sb.append(in).append("<xs:element name=\"").append(f.name()).append("\" type=\"").append(xsType(f)).append('"');
        if (inText) {
            sb.append(" dfdl:representation=\"text\" dfdl:lengthKind=\"delimited\"");
            if (f.type().isNumeric()) {
                sb.append(" dfdl:textNumberRep=\"standard\" dfdl:textNumberPattern=\"")
                        .append(f.type().isInteger() ? "#0" : "#0.###############") .append('"');
            }
        } else {
            switch (f.type()) {
                case STRING -> sb.append(" dfdl:representation=\"text\" dfdl:length=\"")
                        .append(length(f.length(), f, scope)).append('"');
                case BYTES -> sb.append(" dfdl:length=\"").append(length(f.length(), f, scope)).append('"');
                default -> {
                    sb.append(" dfdl:length=\"").append(f.type().fixedWidth()).append('"');
                    if (f.byteOrder() != null && f.byteOrder() != format.defaultByteOrder()) {
                        sb.append(" dfdl:byteOrder=\"")
                                .append(f.byteOrder() == ByteOrder.LITTLE_ENDIAN ? "littleEndian" : "bigEndian").append('"');
                    }
                }
            }
        }
        appendOccurrence(sb, f, scope);
        String doc = KaitaiGenerator.describe(f.semantics());
        if (doc.isEmpty()) {
            sb.append("/>\n");
        } else {
            sb.append(">\n");
            appendDoc(sb, f.semantics(), in + "  ");
            sb.append(in).append("</xs:element>\n");
        }
    }

    private void appendOccurrence(StringBuilder sb, ElementDescription e, Scope scope) {
        if (e.occurrence() instanceof Occurrence.Repeated r) {
            sb.append(" minOccurs=\"0\" maxOccurs=\"unbounded\" dfdl:occursCountKind=\"expression\" dfdl:occursCount=\"")
                    .append(xmlEscape("{ xs:unsignedLong(" + r.count().render(EngineSyntax.dfdl(scope, e.id(), 0)) + ") }"))
                    .append('"');
        } else if (e.occurrence() instanceof Occurrence.UntilEnd) {
            sb.append(" minOccurs=\"0\" maxOccurs=\"unbounded\" dfdl:occursCountKind=\"implicit\"");
        } else if (e.occurrence() instanceof Occurrence.Optional o) {
            sb.append(" minOccurs=\"0\" maxOccurs=\"1\" dfdl:occursCountKind=\"expression\" dfdl:occursCount=\"")
                    .append(xmlEscape("{ if (" + o.condition().render(EngineSyntax.dfdl(scope, e.id(), 0))
                            + ") then 1 else 0 }"))
                    .append('"');
        }
    }

    private String length(Expression length, FieldDescription f, Scope scope) {
        if (length instanceof Expression.IntLiteral lit) {
            return Long.toString(lit.value());
        }
        return xmlEscape("{ xs:unsignedLong(" + length.render(EngineSyntax.dfdl(scope, f.id(), 0)) + ") }");
    }

    private static String xsType(FieldDescription f) {
        return switch (f.type()) {
            case INT8 -> "xs:byte";
            case UINT8 -> "xs:unsignedByte";
            case INT16 -> "xs:short";
            case UINT16 -> "xs:unsignedShort";
            case INT32 -> "xs:int";
            case UINT32 -> "xs:unsignedInt";
            case INT64 -> "xs:long";
            case UINT64 -> "xs:unsignedLong";
            case FLOAT32 -> "xs:float";
            case FLOAT64 -> "xs:double";
            case STRING -> "xs:string";
            case BYTES -> "xs:hexBinary";
        };
    }

    private void appendDoc(StringBuilder sb, Semantics s, String in) {
        String doc = KaitaiGenerator.describe(s);
        if (!doc.isEmpty()) {
            sb.append(in).append("<xs:annotation>\n");
            sb.append(in).append("  <xs:documentation>").append(xmlEscape(doc)).append("</xs:documentation>\n");
            sb.append(in).append("</xs:annotation>\n");
        }
    }

    /**
     * A delimiter as a DFDL string literal: character entities for control and
     * space characters, {@code %%} for a literal percent sign. A newline as a
     * terminator becomes {@code %NL;}, which also accepts CRLF.
     */
    static String dfdlLiteral(String s, boolean terminator) {
        if (terminator && s.equals("\n")) {
            // %ES; (empty) as an alternative lets the last record end at the end of the data.
            return "%NL; %ES;";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '%' -> sb.append("%%");
                case '\n' -> sb.append("%LF;");
                case '\r' -> sb.append("%CR;");
                case '\t' -> sb.append("%HT;");
                case ' ' -> sb.append("%SP;");
                default -> sb.append(xmlEscape(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
