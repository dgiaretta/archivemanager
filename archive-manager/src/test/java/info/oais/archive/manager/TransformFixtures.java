package info.oais.archive.manager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Two formats for testing Transformations: a binary star catalogue (as in
 * examples/topcat) and a format of star positions to transform it into -- a
 * count, then per star a shorter name, right ascension in radians and
 * declination as a single-precision number.
 */
final class TransformFixtures {

    private TransformFixtures() {
    }

    private static final String HEAD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:dfdl="http://www.ogf.org/dfdl/dfdl-1.0/"
                       targetNamespace="urn:test:%1$s" xmlns:tns="urn:test:%1$s" elementFormDefault="qualified">
              <xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>
              <xs:annotation>
                <xs:appinfo source="http://www.ogf.org/dfdl/">
                  <dfdl:defineFormat name="binaryDefaults">
                    <dfdl:format ref="tns:GeneralFormat" representation="binary" byteOrder="bigEndian"
                                 binaryNumberRep="binary" lengthUnits="bytes" lengthKind="explicit"
                                 encoding="US-ASCII" occursCountKind="implicit"/>
                  </dfdl:defineFormat>
                  <dfdl:format ref="tns:binaryDefaults"/>
                </xs:appinfo>
              </xs:annotation>
            """;

    static final String STARS = HEAD.formatted("stars") + """
              <xs:element name="bright_star_catalogue" dfdl:lengthKind="implicit">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="star" dfdl:lengthKind="implicit" minOccurs="0" maxOccurs="unbounded">
                      <xs:annotation>
                        <xs:appinfo source="http://www.ogf.org/dfdl/"><dfdl:assert testKind="pattern" testPattern="(?s)." message="No data left"/></xs:appinfo>
                      </xs:annotation>
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="name" type="xs:string" dfdl:representation="text" dfdl:length="12"/>
                          <xs:element name="ra" type="xs:double" dfdl:length="8"/>
                          <xs:element name="dec" type="xs:double" dfdl:length="8"/>
                          <xs:element name="vmag" type="xs:float" dfdl:length="4"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    static final String POSITIONS = HEAD.formatted("positions") + """
              <xs:element name="positions" dfdl:lengthKind="implicit">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="count" type="xs:unsignedInt" dfdl:length="4"/>
                    <xs:element name="star" dfdl:lengthKind="implicit" minOccurs="0" maxOccurs="unbounded"
                                dfdl:occursCountKind="expression" dfdl:occursCount="{ ../tns:count }">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="name" type="xs:string" dfdl:representation="text" dfdl:length="12"/>
                          <xs:element name="ra_rad" type="xs:double" dfdl:length="8"/>
                          <xs:element name="dec" type="xs:float" dfdl:length="4"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** Sirius, Canopus and Vega. */
    static final Object[][] CATALOGUE = {
            {"Sirius", 101.287155, -16.716116, -1.46f},
            {"Canopus", 95.987958, -52.695661, -0.74f},
            {"Vega", 279.234735, 38.783689, 0.03f}};

    static final String MAPPING = """
            count = count(star)
            for star in star
            star.name = star.name
            star.ra_rad = star.ra * 0.017453292519943295
            star.dec = star.dec
            """;

    static byte[] catalogue() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (Object[] star : CATALOGUE) {
                out.write(String.format("%-12s", star[0]).getBytes(StandardCharsets.US_ASCII));
                out.writeDouble((Double) star[1]);
                out.writeDouble((Double) star[2]);
                out.writeFloat((Float) star[3]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
