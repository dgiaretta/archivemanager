package info.oais.infomodel.structure.dfdl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;

class DfdlSchemaOutlineTest {

	@Test
	void readsTheElementTreeOfASchema() throws Exception {
		SchemaElement point = DfdlSchemaOutline.read(Files.readString(Path.of(getClass().getResource("/point.dfdl.xsd")
				.toURI())));

		assertEquals("point", point.name());
		assertEquals("urn:oais:structure:demo:point", point.namespace());
		assertFalse(point.isValue());
		assertEquals(List.of("x", "y", "labelLen", "label"), point.children().stream().map(SchemaElement::name).toList());
		SchemaElement x = point.child("x").orElseThrow();
		assertEquals("int", x.valueType());
		assertTrue(x.isInteger());
		assertEquals("urn:oais:structure:demo:point", x.namespace());
		assertFalse(x.repeats());
		assertEquals("unsignedByte", point.child("labelLen").orElseThrow().valueType());
		assertEquals("string", point.child("label").orElseThrow().valueType());
	}

	private static final String CHOICES = """
			<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:dfdl="http://www.ogf.org/dfdl/dfdl-1.0/"
			           xmlns:t="urn:t" targetNamespace="urn:t">
			  <xs:simpleType name="kelvin"><xs:restriction base="xs:float"/></xs:simpleType>
			  <xs:complexType name="reading">
			    <xs:sequence>
			      <xs:element name="temp" type="t:kelvin">
			        <xs:annotation><xs:documentation>Air temperature</xs:documentation></xs:annotation>
			      </xs:element>
			    </xs:sequence>
			  </xs:complexType>
			  <xs:element name="file">
			    <xs:complexType>
			      <xs:sequence>
			        <xs:element name="count" type="xs:unsignedInt" dfdl:outputValueCalc="{ fn:count(../reading) }"/>
			        <xs:element name="reading" type="t:reading" minOccurs="0" maxOccurs="unbounded"/>
			        <xs:choice>
			          <xs:element name="a" type="xs:int"/>
			          <xs:element name="b" type="xs:string"/>
			        </xs:choice>
			      </xs:sequence>
			    </xs:complexType>
			  </xs:element>
			</xs:schema>
			""";

	@Test
	void followsNamedTypesAndMarksComputedValuesAndAlternatives() {
		SchemaElement file = DfdlSchemaOutline.read(CHOICES);

		assertEquals("urn:t", file.namespace());
		SchemaElement count = file.child("count").orElseThrow();
		assertTrue(count.computed());
		SchemaElement reading = file.child("reading").orElseThrow();
		assertTrue(reading.repeats());
		assertEquals(0, reading.minOccurs());
		assertNull(reading.namespace(), "a local element is unqualified unless the schema says otherwise");
		SchemaElement temp = reading.child("temp").orElseThrow();
		assertEquals("float", temp.valueType());
		assertEquals("Air temperature", temp.documentation());
		int group = file.child("a").orElseThrow().alternativeGroup();
		assertTrue(group > 0);
		assertEquals(group, file.child("b").orElseThrow().alternativeGroup());
		assertEquals(0, reading.alternativeGroup());
	}

	@Test
	void saysWhatItCantFind() {
		StructureInterpretationException e = assertThrows(StructureInterpretationException.class,
				() -> DfdlSchemaOutline.read(CHOICES.replace("type=\"t:reading\"", "type=\"t:missing\"")));
		assertTrue(e.getMessage().contains("t:missing"), e.getMessage());
		assertThrows(StructureInterpretationException.class, () -> DfdlSchemaOutline.read("<notASchema/>"));
	}

	@Test
	void encodesAnInfosetBuiltFromValuesAlone() throws Exception {
		DfdlStructureRepInfo point = new DfdlStructureRepInfo(new DfdlFormatSpecification(
				getClass().getResource("/point.dfdl.xsd").toURI()));
		String ns = "urn:oais:structure:demo:point";
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document infoset = factory.newDocumentBuilder().newDocument();
		Element root = infoset.createElementNS(ns, "point");
		infoset.appendChild(root);
		for (String[] value : new String[][] {{"x", "3"}, {"y", "-4"}, {"labelLen", "5"}, {"label", "hello"}}) {
			Element e = infoset.createElementNS(ns, value[0]);
			e.setTextContent(value[1]);
			root.appendChild(e);
		}

		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(expected)) {
			out.writeInt(3);
			out.writeInt(-4);
			out.writeByte(5);
			out.write("hello".getBytes(StandardCharsets.US_ASCII));
		}
		assertArrayEquals(expected.toByteArray(), point.encode(infoset));
	}
}
