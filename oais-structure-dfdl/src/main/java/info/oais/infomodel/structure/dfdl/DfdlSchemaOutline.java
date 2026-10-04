package info.oais.infomodel.structure.dfdl;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import info.oais.infomodel.structure.StructureInterpretationException;

/**
 * The element tree a DFDL schema describes: the elements of its infoset, in
 * order, with how often each occurs and, for a value, its type -- what's
 * needed to build an infoset from values alone and
 * {@link DfdlStructureRepInfo#encode encode} it, and to show what a file in
 * the format holds.
 *
 * <p>Reads the schema as XML Schema, from its first global element (the one
 * Daffodil uses by default), following named complex and simple types and
 * element references within the same document; sequences nest, and a
 * choice's elements are alternatives. Elements whose value the schema
 * computes when writing ({@code dfdl:outputValueCalc}) are marked, and hidden
 * groups, which aren't part of the infoset, are left out. Included or
 * imported schemas aren't read, so a type or element only they define is
 * reported as unknown.</p>
 */
public final class DfdlSchemaOutline {

	private static final String XS = "http://www.w3.org/2001/XMLSchema";
	private static final String DFDL = "http://www.ogf.org/dfdl/dfdl-1.0/";

	/** A count with no upper bound. */
	public static final int UNBOUNDED = -1;

	/**
	 * One element of the infoset.
	 *
	 * @param name             its local name
	 * @param namespace        its namespace in the infoset, or null for none
	 * @param minOccurs        how often it must occur
	 * @param maxOccurs        how often it may occur; {@link #UNBOUNDED} for no limit
	 * @param valueType        for a value: its XML Schema type's local name (e.g. {@code double},
	 *                         {@code string}); null for an element holding other elements
	 * @param computed         whether the schema computes its value when writing
	 *                         ({@code dfdl:outputValueCalc}), so an infoset leaves it out
	 * @param alternativeGroup 0, or the number of the choice it is an alternative in: at most one
	 *                         element of each group is in the infoset
	 * @param documentation    the schema's documentation of it, or null
	 * @param children         the elements it holds, in order
	 */
	public record SchemaElement(String name, String namespace, int minOccurs, int maxOccurs, String valueType,
			boolean computed, int alternativeGroup, String documentation, List<SchemaElement> children) {

		public SchemaElement {
			children = List.copyOf(children);
		}

		public boolean isValue() {
			return valueType != null;
		}

		/** Whether it may occur more than once. */
		public boolean repeats() {
			return maxOccurs == UNBOUNDED || maxOccurs > 1;
		}

		public Optional<SchemaElement> child(String name) {
			return children.stream().filter(c -> c.name.equals(name)).findFirst();
		}

		/** Whether its value is a number. */
		public boolean isNumeric() {
			return valueType != null && NUMERIC.contains(valueType);
		}

		/** Whether its value is a whole number. */
		public boolean isInteger() {
			return isNumeric() && !List.of("float", "double", "decimal").contains(valueType);
		}
	}

	private static final List<String> NUMERIC = List.of("byte", "short", "int", "long", "integer", "unsignedByte",
			"unsignedShort", "unsignedInt", "unsignedLong", "nonNegativeInteger", "positiveInteger",
			"negativeInteger", "nonPositiveInteger", "float", "double", "decimal");

	private final Element schema;
	private final String targetNamespace;
	private final boolean qualified;
	private int groups;

	private DfdlSchemaOutline(Element schema) {
		this.schema = schema;
		String tns = schema.getAttribute("targetNamespace");
		this.targetNamespace = tns.isEmpty() ? null : tns;
		this.qualified = "qualified".equals(schema.getAttribute("elementFormDefault"));
	}

	/**
	 * The element tree of the DFDL schema {@code schemaText}, from its first
	 * global element.
	 *
	 * @throws StructureInterpretationException if it isn't an XML Schema with a global element, or uses a type
	 *                                           or element it doesn't define
	 */
	public static SchemaElement read(String schemaText) {
		Document document;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(schemaText)));
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new StructureInterpretationException("The DFDL schema isn't readable XML: " + e.getMessage(), e);
		}
		Element root = document.getDocumentElement();
		if (!XS.equals(root.getNamespaceURI()) || !"schema".equals(root.getLocalName())) {
			throw new StructureInterpretationException("The DFDL schema isn't an XML Schema (no xs:schema element)");
		}
		DfdlSchemaOutline outline = new DfdlSchemaOutline(root);
		Element first = children(root, "element").stream().findFirst().orElseThrow(() ->
				new StructureInterpretationException("The DFDL schema declares no global element"));
		return outline.element(first, true, 0, new ArrayList<>());
	}

	private SchemaElement element(Element declaration, boolean global, int alternativeGroup, List<String> visiting) {
		Element decl = declaration;
		boolean isGlobal = global;
		String ref = decl.getAttribute("ref");
		if (!ref.isEmpty()) {
			decl = named("element", ref);
			isGlobal = true;
		}
		String name = decl.getAttribute("name");
		int min = occurs(declaration.getAttribute("minOccurs"), 1);
		int max = occurs(declaration.getAttribute("maxOccurs"), 1);
		String namespace = isGlobal || qualified || "qualified".equals(decl.getAttribute("form")) ? targetNamespace
				: null;
		boolean computed = decl.hasAttributeNS(DFDL, "outputValueCalc") || hasDfdlProperty(decl, "outputValueCalc");
		String documentation = documentation(decl);

		String type = decl.getAttribute("type");
		Element complex = first(decl, "complexType");
		Element simple = first(decl, "simpleType");
		if (complex == null && !type.isEmpty() && !XS.equals(namespaceOf(decl, type))) {
			Element namedType = namedOrNull("complexType", type);
			if (namedType != null) {
				complex = namedType;
			} else {
				simple = named("simpleType", type);
			}
		}
		if (complex == null) {
			String valueType = simple != null ? baseType(simple) : type.isEmpty() ? "string" : localName(type);
			return new SchemaElement(name, namespace, min, max, valueType, computed, alternativeGroup, documentation,
					List.of());
		}
		String key = name + "@" + System.identityHashCode(complex);
		if (visiting.contains(key)) {
			throw new StructureInterpretationException("The DFDL schema's element '" + name + "' contains itself");
		}
		visiting.add(key);
		List<SchemaElement> children = new ArrayList<>();
		model(complex, children, 0, visiting);
		visiting.remove(visiting.size() - 1);
		return new SchemaElement(name, namespace, min, max, null, computed, alternativeGroup, documentation, children);
	}

	/** Adds the elements of {@code container}'s content model -- sequences, choices, elements -- to {@code into}. */
	private void model(Element container, List<SchemaElement> into, int alternativeGroup, List<String> visiting) {
		for (Element child : children(container, null)) {
			switch (child.getLocalName()) {
				case "sequence" -> {
					if (!child.getAttributeNS(DFDL, "hiddenGroupRef").isEmpty()) {
						continue;
					}
					model(child, into, alternativeGroup, visiting);
				}
				case "choice" -> model(child, into, ++groups, visiting);
				case "element" -> into.add(element(child, false, alternativeGroup, visiting));
				case "group" -> {
					Element group = named("group", child.getAttribute("ref"));
					model(group, into, alternativeGroup, visiting);
				}
				default -> {
					// annotations and the like
				}
			}
		}
	}

	private static boolean hasDfdlProperty(Element decl, String property) {
		for (Element annotation : children(decl, "annotation")) {
			for (Element appinfo : children(annotation, "appinfo")) {
				for (Node n = appinfo.getFirstChild(); n != null; n = n.getNextSibling()) {
					if (n instanceof Element e && DFDL.equals(e.getNamespaceURI())
							&& (e.hasAttribute(property) || "property".equals(e.getLocalName())
									&& property.equals(e.getAttribute("name")))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String documentation(Element decl) {
		for (Element annotation : children(decl, "annotation")) {
			for (Element doc : children(annotation, "documentation")) {
				String text = doc.getTextContent().strip();
				if (!text.isEmpty()) {
					return text;
				}
			}
		}
		return null;
	}

	/** A simple type's built-in base type, following named simple types. */
	private String baseType(Element simple) {
		Element restriction = first(simple, "restriction");
		if (restriction == null) {
			return "string";
		}
		String base = restriction.getAttribute("base");
		if (base.isEmpty() || XS.equals(namespaceOf(restriction, base))) {
			return base.isEmpty() ? "string" : localName(base);
		}
		return baseType(named("simpleType", base));
	}

	private Element named(String kind, String qname) {
		Element found = namedOrNull(kind, qname);
		if (found == null) {
			throw new StructureInterpretationException("The DFDL schema uses " + kind + " '" + qname
					+ "', which it doesn't define (included or imported schemas aren't read)");
		}
		return found;
	}

	private Element namedOrNull(String kind, String qname) {
		String local = localName(qname);
		return children(schema, kind).stream().filter(e -> local.equals(e.getAttribute("name"))).findFirst()
				.orElse(null);
	}

	private static String namespaceOf(Element context, String qname) {
		int colon = qname.indexOf(':');
		return context.lookupNamespaceURI(colon < 0 ? null : qname.substring(0, colon));
	}

	private static String localName(String qname) {
		return qname.substring(qname.indexOf(':') + 1);
	}

	private static int occurs(String value, int fallback) {
		if (value.isEmpty()) {
			return fallback;
		}
		return "unbounded".equals(value) ? UNBOUNDED : Integer.parseInt(value.strip());
	}

	private static Element first(Element parent, String localName) {
		List<Element> found = children(parent, localName);
		return found.isEmpty() ? null : found.get(0);
	}

	/** {@code parent}'s XML Schema child elements called {@code localName}, or all of them if it's null. */
	private static List<Element> children(Element parent, String localName) {
		List<Element> result = new ArrayList<>();
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e && XS.equals(e.getNamespaceURI())
					&& (localName == null || localName.equals(e.getLocalName()))) {
				result.add(e);
			}
		}
		return result;
	}
}
