package info.oais.infomodel.structure.semantic;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Shared XML-reading mechanics for this package's external view
 * specification readers ({@link TableViewSpecificationReader},
 * {@link TimeSeriesViewSpecificationReader}, {@link VectorViewSpecificationReader},
 * {@link ImageViewSpecificationReader}): parsing the file, walking direct
 * child elements (deliberately not {@link Element#getElementsByTagName},
 * which also matches deeper descendants), reading a required attribute,
 * resolving a {@code type}/{@code pixelType} name to a {@link Class}, and the
 * {@code <rows select="...">} row/pixel/coordinate-tuple selection
 * convention every one of those file formats shares. Not a view
 * specification reader itself - each of those four classes' own Javadoc
 * documents its own file format and has a runnable example.
 */
final class ViewSpecificationXml {

	private static final Map<String, Class<?>> SHORT_TYPE_NAMES = Map.ofEntries(
			Map.entry("int", Integer.class),
			Map.entry("integer", Integer.class),
			Map.entry("long", Long.class),
			Map.entry("short", Short.class),
			Map.entry("byte", Byte.class),
			Map.entry("double", Double.class),
			Map.entry("float", Float.class),
			Map.entry("boolean", Boolean.class),
			Map.entry("string", String.class),
			Map.entry("biginteger", BigInteger.class),
			Map.entry("bigdecimal", BigDecimal.class),
			Map.entry("object", Object.class));

	private ViewSpecificationXml() {
	}

	/**
	 * Parses {@code location} and returns its document element.
	 *
	 * @throws ViewSpecificationException if the file cannot be read or parsed
	 */
	static Element parseRoot(URI location) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			// Best-effort XXE hardening for what is otherwise a small, locally-authored
			// file; if this JAXP implementation does not support the feature, proceed
			// without it rather than failing the whole read over it.
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (ParserConfigurationException e) {
			// ignored - see comment above
		}
		try (InputStream in = location.toURL().openStream()) {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(in);
			return document.getDocumentElement();
		} catch (IOException | ParserConfigurationException | SAXException e) {
			throw new ViewSpecificationException("Unable to read view specification " + location, e);
		}
	}

	static List<Element> directChildElements(Element parent, String tagName) {
		List<Element> result = new ArrayList<>();
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && ((Element) child).getTagName().equals(tagName)) {
				result.add((Element) child);
			}
		}
		return result;
	}

	static Element requiredSingleChild(Element parent, String tagName, URI location) {
		List<Element> matches = directChildElements(parent, tagName);
		if (matches.isEmpty()) {
			throw new ViewSpecificationException(
					"<" + parent.getTagName() + "> must have a <" + tagName + "> child in " + location);
		}
		return matches.get(0);
	}

	static String requiredAttribute(Element element, String attributeName, URI location) {
		if (!element.hasAttribute(attributeName)) {
			throw new ViewSpecificationException(
					"<" + element.getTagName() + "> is missing its required \"" + attributeName + "\" attribute in "
							+ location);
		}
		return element.getAttribute(attributeName);
	}

	static Class<?> classFor(String typeName, URI location) {
		Class<?> shortName = SHORT_TYPE_NAMES.get(typeName.toLowerCase(Locale.ROOT));
		if (shortName != null) {
			return shortName;
		}
		try {
			return Class.forName(typeName);
		} catch (ClassNotFoundException e) {
			throw new ViewSpecificationException(
					"Unknown type \"" + typeName + "\" in " + location + " (expected one of " + SHORT_TYPE_NAMES.keySet()
							+ ", or a fully-qualified class name)",
					e);
		}
	}

	/**
	 * The {@code select="self"} / {@code select="children" name="..."} /
	 * {@code select="array" name="..."} convention shared by {@code <rows>},
	 * {@code <pixels>} and {@code <coordinates>} across this package's view
	 * specification file formats - see {@link TableViewSpecificationReader}'s
	 * Javadoc for the canonical description, with an example.
	 *
	 * <p>{@code "children"} and {@code "array"} both select a repeated
	 * element, but they match {@link StructureNodeKind}'s two different ways
	 * an engine can represent one (see that enum's Javadoc): {@code "children"}
	 * expects the DFDL/DRB convention - the repeated element's own name,
	 * naming several same-named {@link StructureNodeKind#COMPOSITE} siblings
	 * directly under the row selector's starting node; {@code "array"}
	 * expects the Kaitai Struct convention - a single named
	 * {@link StructureNodeKind#ARRAY} child (its {@code repeat:} field's own
	 * name, not the repeated element type's name, since array elements are
	 * indexed rather than named) whose own children are the rows.</p>
	 */
	static Function<StructureNode, List<StructureNode>> rowSelectorFor(Element element, URI location) {
		String select = requiredAttribute(element, "select", location);
		if (select.equals("self")) {
			return List::of;
		}
		if (select.equals("children")) {
			String name = requiredAttribute(element, "name", location);
			return root -> root.childrenNamed(name);
		}
		if (select.equals("array")) {
			String name = requiredAttribute(element, "name", location);
			return root -> {
				StructureNode container = root.child(name)
						.orElseThrow(() -> new ViewSpecificationException("<" + element.getTagName()
								+ " select=\"array\" name=\"" + name + "\"> found no child named \"" + name + "\" in "
								+ location));
				if (container.getKind() != StructureNodeKind.ARRAY) {
					throw new ViewSpecificationException("<" + element.getTagName() + " select=\"array\" name=\"" + name
							+ "\">'s \"" + name + "\" child is " + container.getKind() + ", not ARRAY, in " + location);
				}
				return container.getChildren();
			};
		}
		throw new ViewSpecificationException(
				"<" + element.getTagName() + " select=\"" + select + "\"> is not recognised (expected \"self\", "
						+ "\"children\" or \"array\") in " + location);
	}

	/**
	 * A {@code <column name="..." type="..." child="..."?>} element - the
	 * convention {@link TableViewSpecificationReader} documents in full -
	 * turned into a {@link ColumnMapping}.
	 */
	static ColumnMapping columnMappingFor(Element columnElement, URI location) {
		String name = requiredAttribute(columnElement, "name", location);
		Class<?> columnClass = classFor(requiredAttribute(columnElement, "type", location), location);
		String childName = columnElement.hasAttribute("child") ? columnElement.getAttribute("child") : name;
		return new ColumnMapping(name, columnClass,
				row -> row.child(childName).flatMap(StructureNode::getValue).orElse(null));
	}
}
