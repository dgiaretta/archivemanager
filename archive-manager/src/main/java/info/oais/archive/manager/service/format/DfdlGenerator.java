package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import org.springframework.stereotype.Component;

/**
 * Generates a DFDL schema (Data Format Description Language v1.0, OGF
 * GFD.207) from a {@link FormatDefinition}, as an ordinary XML Schema
 * document with {@code dfdl:} annotations on top -- the same shape as the
 * demo's own {@code point.dfdl.xsd} (see
 * {@code oais-structure-adapters-data.ttl}'s comments). Only applies to
 * {@link FormatDefinitionKind#BYTE_LAYOUT}, for the same reason
 * {@link KaitaiGenerator} is byte-layout-only.
 *
 * <p>Hand-built text (a Java text block per field, {@code .formatted()}),
 * not a DOM-building/Transformer approach -- matches this codebase's existing
 * convention of building structured text directly rather than through a
 * generic builder API (see every SPARQL query in {@code GraphService}/
 * {@code ArchiveService}).
 */
@Component
public class DfdlGenerator {

    /** @return the generated {@code .dfdl.xsd} XML text, or {@code null} if {@code def} isn't byte-layout. */
    public String generate(FormatDefinition def) {
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return null;
        }
        String rootName = FormatIdentifiers.snakeCase(def.getName());
        String namespace = "urn:archive-manager:format:" + rootName;
        String defaultByteOrder = def.getDefaultByteOrder() == ByteOrder.LITTLE_ENDIAN ? "littleEndian" : "bigEndian";

        StringBuilder elements = new StringBuilder();
        for (FormatField field : def.getFields()) {
            elements.append(elementFor(field, def.getDefaultByteOrder()));
        }

        String docComment = def.getNotes().isBlank() ? "" : """

                  <xs:annotation>
                    <xs:documentation>%s</xs:documentation>
                  </xs:annotation>
                """.formatted(xmlEscape(def.getNotes()));

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:dfdl="http://www.ogf.org/dfdl/dfdl-1.0/"
                           targetNamespace="%s"
                           xmlns:tns="%s"
                           elementFormDefault="qualified">

                  <xs:annotation>
                    <xs:appinfo source="http://www.ogf.org/dfdl/dfdl-1.0/">
                      <dfdl:format representation="binary" byteOrder="%s" binaryNumberRep="binary"
                                   lengthUnits="bytes" lengthKind="explicit" encoding="ASCII"/>
                    </xs:appinfo>
                  </xs:annotation>
                %s
                  <xs:element name="%s">
                    <xs:complexType>
                      <xs:sequence>
                %s      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """.formatted(namespace, namespace, defaultByteOrder, docComment, rootName, elements);
    }

    private String elementFor(FormatField field, ByteOrder defaultOrder) {
        String name = FormatIdentifiers.snakeCase(field.name());
        String byteOrderAttr = field.byteOrder() == null || field.byteOrder() == defaultOrder
                ? "" : " dfdl:byteOrder=\"" + (field.byteOrder() == ByteOrder.LITTLE_ENDIAN ? "littleEndian" : "bigEndian") + "\"";
        String xsType;
        String lengthAttr;
        switch (field.type()) {
            case INT8 -> { xsType = "xs:byte"; lengthAttr = " dfdl:length=\"1\""; }
            case UINT8 -> { xsType = "xs:unsignedByte"; lengthAttr = " dfdl:length=\"1\""; }
            case INT16 -> { xsType = "xs:short"; lengthAttr = " dfdl:length=\"2\""; }
            case UINT16 -> { xsType = "xs:unsignedShort"; lengthAttr = " dfdl:length=\"2\""; }
            case INT32 -> { xsType = "xs:int"; lengthAttr = " dfdl:length=\"4\""; }
            case UINT32 -> { xsType = "xs:unsignedInt"; lengthAttr = " dfdl:length=\"4\""; }
            case INT64 -> { xsType = "xs:long"; lengthAttr = " dfdl:length=\"8\""; }
            case UINT64 -> { xsType = "xs:unsignedLong"; lengthAttr = " dfdl:length=\"8\""; }
            case FLOAT32 -> { xsType = "xs:float"; lengthAttr = " dfdl:length=\"4\""; }
            case FLOAT64 -> { xsType = "xs:double"; lengthAttr = " dfdl:length=\"8\""; }
            case ASCII_STRING -> {
                xsType = "xs:string";
                lengthAttr = " dfdl:length=\"" + (field.lengthBytes() == null ? 1 : field.lengthBytes()) + "\" dfdl:encoding=\"ASCII\" dfdl:representation=\"text\"";
            }
            default -> {
                xsType = "xs:hexBinary";
                lengthAttr = " dfdl:length=\"" + (field.lengthBytes() == null ? 1 : field.lengthBytes()) + "\"";
            }
        }
        String doc = field.description() == null || field.description().isBlank() ? "" : """
                          <xs:annotation>
                            <xs:documentation>%s</xs:documentation>
                          </xs:annotation>
                """.formatted(xmlEscape(field.description()));
        return """
                        <xs:element name="%s" type="%s"%s%s>
                %s        </xs:element>
                """.formatted(name, xsType, lengthAttr, byteOrderAttr, doc);
    }

    private String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
