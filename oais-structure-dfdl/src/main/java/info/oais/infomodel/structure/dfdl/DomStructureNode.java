package info.oais.infomodel.structure.dfdl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Wraps one {@link Element} of the W3C DOM infoset that
 * {@code org.apache.daffodil.japi.infoset.W3CDOMInfosetOutputter} produces,
 * exposing it as a {@link StructureNode} without copying anything.
 *
 * <p>DFDL's own infoset does not distinguish "repeated element" from
 * "several unrelated same-named siblings" any more than plain XML does, so -
 * consistent with how a DOM document represents both - a repeated DFDL
 * element simply appears as several children of {@link #getKind() COMPOSITE}
 * kind sharing the same {@link #getName()}, rather than as a single ARRAY
 * node. Use {@link StructureNode#childrenNamed(String)} to gather them.</p>
 *
 * <p><b>Byte-range provenance ({@link #getSourceRange()}):</b> the W3C DOM
 * infoset outputter itself does not carry it, so it is recovered,
 * best-effort, from a second, independent parse run by
 * {@link PositionTrackingInfosetOutputter} (see
 * {@link DfdlStructureRepInfo#doApply}) and correlated back to each node
 * here purely by structural position - the sequence of child-element
 * indices from the document root, computed as {@link #getChildren()} builds
 * each child. See {@link PositionTrackingInfosetOutputter}'s Javadoc for why
 * this two-parse approach was chosen and for the real caveat: nothing here
 * is guaranteed to be non-empty, since it depends on whether your installed
 * Daffodil version's infoset element classes expose one of the position
 * accessor method names that class guesses at, which this project could not
 * confirm against a live compile.</p>
 *
 * <p><b>Typed values ({@link #getValue()}):</b> a DOM {@link Element}'s text
 * content is always a {@link String}, regardless of what DFDL simple type
 * the schema declared for it - so on its own, this class could never tell an
 * {@code xs:int} element from an {@code xs:string} one. When a
 * {@code typedValuesByPath} entry is available for this node - recovered by
 * the same {@link PositionTrackingInfosetOutputter} second parse that
 * supplies {@link #getSourceRange()}, see that class's Javadoc on "Typed
 * values" - {@link #getValue()} returns that already-typed value instead of
 * the raw DOM text. Falls back to the DOM text, exactly as before, whenever
 * no typed value was recovered for this node.</p>
 */
final class DomStructureNode implements StructureNode {

	private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

	private final Element element;
	private final Map<List<Integer>, ByteRange> rangesByPath;
	private final Map<List<Integer>, Object> typedValuesByPath;
	private final List<Integer> path;

	/**
	 * Wraps {@code element} with no source-range or typed-value information
	 * available (as before {@link #getSourceRange()} existed). Prefer
	 * {@link #DomStructureNode(Element, Map, Map, List)} when a
	 * {@link PositionTrackingInfosetOutputter} result is available.
	 */
	DomStructureNode(Element element) {
		this(element, Map.of(), Map.of(), List.of());
	}

	/**
	 * @param element           the DOM element this node wraps
	 * @param rangesByPath      ranges recovered by a {@link PositionTrackingInfosetOutputter}
	 *                          run against the same bytes, keyed by child-index path from the
	 *                          document root; possibly empty, never {@code null}
	 * @param typedValuesByPath typed simple-element values recovered by the same
	 *                          {@link PositionTrackingInfosetOutputter} run, keyed the same way;
	 *                          possibly empty, never {@code null}
	 * @param path              this node's own child-index path from the document root
	 *                          ({@code List.of()} for the root itself)
	 */
	DomStructureNode(Element element, Map<List<Integer>, ByteRange> rangesByPath,
			Map<List<Integer>, Object> typedValuesByPath, List<Integer> path) {
		this.element = element;
		this.rangesByPath = rangesByPath;
		this.typedValuesByPath = typedValuesByPath;
		this.path = path;
	}

	@Override
	public String getName() {
		String local = element.getLocalName();
		return local != null ? local : element.getNodeName();
	}

	@Override
	public Optional<String> getTypeName() {
		String xsiType = element.getAttributeNS(XSI_NS, "type");
		if (xsiType != null && !xsiType.isEmpty()) {
			return Optional.of(xsiType);
		}
		return Optional.empty();
	}

	@Override
	public StructureNodeKind getKind() {
		return hasChildElements() ? StructureNodeKind.COMPOSITE : StructureNodeKind.LEAF;
	}

	@Override
	public Optional<Object> getValue() {
		if (getKind() != StructureNodeKind.LEAF) {
			return Optional.empty();
		}
		if (isXsiNil()) {
			return Optional.empty();
		}
		Object typedValue = typedValuesByPath.get(path);
		if (typedValue != null) {
			return Optional.of(typedValue);
		}
		String text = element.getTextContent();
		return Optional.ofNullable(text);
	}

	@Override
	public List<StructureNode> getChildren() {
		List<StructureNode> children = new ArrayList<>();
		NodeList childNodes = element.getChildNodes();
		int index = 0;
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node child = childNodes.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				List<Integer> childPath = new ArrayList<>(path);
				childPath.add(index);
				children.add(new DomStructureNode((Element) child, rangesByPath, typedValuesByPath, childPath));
				index++;
			}
		}
		return children;
	}

	@Override
	public Map<String, Object> getAttributes() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		var attrs = element.getAttributes();
		if (attrs != null) {
			for (int i = 0; i < attrs.getLength(); i++) {
				Attr attr = (Attr) attrs.item(i);
				if (XSI_NS.equals(attr.getNamespaceURI())) {
					// xsi:type / xsi:nil are surfaced via getTypeName()/getValue(); skip here to avoid duplication.
					continue;
				}
				attributes.put(attr.getName(), attr.getValue());
			}
		}
		return attributes;
	}

	@Override
	public Optional<ByteRange> getSourceRange() {
		return Optional.ofNullable(rangesByPath.get(path));
	}

	private boolean hasChildElements() {
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
				return true;
			}
		}
		return false;
	}

	private boolean isXsiNil() {
		return "true".equals(element.getAttributeNS(XSI_NS, "nil"));
	}
}
