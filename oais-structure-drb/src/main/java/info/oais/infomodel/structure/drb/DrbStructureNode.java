package info.oais.infomodel.structure.drb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Wraps one DRB node (an instance of, typically, {@code fr.gael.drb.DrbNode}
 * or the equivalent in whichever DRB/DRB-Cortex distribution you are using)
 * as a {@link StructureNode}, entirely by reflection - see this module's
 * {@code README-DRB.md} and {@link ReflectiveApi} for why, and for how to
 * adjust the candidate method names below if your DRB version's API differs.
 */
final class DrbStructureNode implements StructureNode {

	private final Object drbNode;

	// Memoized: DRB is documented (see DrbStructureRepInfo) as potentially doing
	// real, lazy I/O when a node's children are asked for. getKind() and
	// getChildren() both need that list; without caching it here, a plain
	// traversal that checks kind-then-children (e.g. StructureNodePrinter) would
	// trigger that lazy work twice per node.
	private List<Object> cachedChildrenRaw;

	DrbStructureNode(Object drbNode) {
		this.drbNode = drbNode;
	}

	@Override
	public String getName() {
		return ReflectiveApi.invokeFirst(drbNode, "getName")
				.map(Object::toString)
				.orElse("?");
	}

	@Override
	public Optional<String> getTypeName() {
		return ReflectiveApi.invokeFirst(drbNode, "getNamespaceUri", "getType")
				.map(Object::toString);
	}

	@Override
	public StructureNodeKind getKind() {
		return childrenRaw().isEmpty() ? StructureNodeKind.LEAF : StructureNodeKind.COMPOSITE;
	}

	@Override
	public Optional<Object> getValue() {
		if (getKind() != StructureNodeKind.LEAF) {
			return Optional.empty();
		}
		return ReflectiveApi.invokeFirst(drbNode, "getValue");
	}

	@Override
	public List<StructureNode> getChildren() {
		return childrenRaw().stream().<StructureNode>map(DrbStructureNode::new).toList();
	}

	@Override
	public Map<String, Object> getAttributes() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		for (Object attribute : attributesRaw()) {
			String name = ReflectiveApi.invokeFirst(attribute, "getName").map(Object::toString).orElse(null);
			Object value = ReflectiveApi.invokeFirst(attribute, "getValue").orElse(null);
			if (name != null) {
				attributes.put(name, value);
			}
		}
		return attributes;
	}

	@Override
	public Optional<ByteRange> getSourceRange() {
		// Unlike getName/getValue/getChildren/getAttributes above, which reflect
		// against a node shape README-DRB.md documents as long-standing and stable
		// across DRB/DRB-Cortex versions, no byte/bit-offset accessor is part of
		// that documented shape - DRB's public node API presents a semantic tree,
		// not a flat parse trace with positions attached to it. This project could
		// not find a candidate method name for one worth enough confidence to
		// guess at here the way ReflectiveApi.invokeFirst does for the other
		// accessors: a wrong number silently mislabelled as a byte offset would be
		// worse than reporting nothing. If your installed DRB/DRB-Cortex version
		// does expose per-node position (some underlying "item" representations
		// do, depending on the backing format), wire it in exactly the same way as
		// the accessors above: add candidate method names and call
		// ReflectiveApi.invokeFirst(drbNode, ...) here.
		return Optional.empty();
	}

	private List<Object> childrenRaw() {
		if (cachedChildrenRaw == null) {
			cachedChildrenRaw = ReflectiveApi.invokeFirst(drbNode, "getChildrenList", "getChildren")
					.map(ReflectiveApi::asList)
					.orElse(List.of());
		}
		return cachedChildrenRaw;
	}

	private List<Object> attributesRaw() {
		return ReflectiveApi.invokeFirst(drbNode, "getAttributesList", "getAttributes")
				.map(ReflectiveApi::asList)
				.orElse(List.of());
	}
}
