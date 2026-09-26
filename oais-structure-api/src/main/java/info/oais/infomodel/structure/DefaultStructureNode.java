package info.oais.infomodel.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A plain, immutable, in-memory {@link StructureNode}. Not used internally
 * by the DRB, Kaitai Struct or DFDL adapters (each of those wraps its native
 * result directly, for efficiency and so a huge parsed structure is not
 * copied), but useful for:
 *
 * <ul>
 * <li>hand-building a {@link StructureNode} tree, e.g. to represent the
 * result of some other, non-binary kind of Structure Representation
 * Information;</li>
 * <li>fixtures in tests of code that consumes {@link StructureNode}s;</li>
 * <li>{@link #copyOf(StructureNode)}, to detach a tree from whatever engine
 * object it was originally backed by (and the resources - open streams,
 * DOM trees, native buffers - that engine object might be holding).</li>
 * </ul>
 */
public final class DefaultStructureNode implements StructureNode {

	private final String name;
	private final String typeName;
	private final StructureNodeKind kind;
	private final Object value;
	private final List<StructureNode> children;
	private final Map<String, Object> attributes;
	private final ByteRange sourceRange;

	private DefaultStructureNode(Builder b) {
		this.name = Objects.requireNonNull(b.name, "name");
		this.typeName = b.typeName;
		this.kind = Objects.requireNonNull(b.kind, "kind");
		this.value = b.value;
		this.children = List.copyOf(b.children);
		this.attributes = Map.copyOf(b.attributes);
		this.sourceRange = b.sourceRange;

		if (kind == StructureNodeKind.LEAF && !children.isEmpty()) {
			throw new IllegalStateException("A LEAF node ('" + name + "') must not have children");
		}
		if (kind != StructureNodeKind.LEAF && value != null) {
			throw new IllegalStateException("A " + kind + " node ('" + name + "') must not carry a value");
		}
	}

	public static Builder builder(String name, StructureNodeKind kind) {
		return new Builder(name, kind);
	}

	public static DefaultStructureNode leaf(String name, Object value) {
		return builder(name, StructureNodeKind.LEAF).value(value).build();
	}

	/**
	 * Recursively copies any {@link StructureNode} (however it is backed)
	 * into a detached, engine-independent {@link DefaultStructureNode} tree.
	 *
	 * @param source the node (and its descendants) to copy
	 * @return an equivalent, fully detached tree
	 */
	public static DefaultStructureNode copyOf(StructureNode source) {
		Builder b = builder(source.getName(), source.getKind())
				.value(source.getValue().orElse(null))
				.attributes(source.getAttributes())
				.sourceRange(source.getSourceRange().orElse(null));
		source.getTypeName().ifPresent(b::typeName);
		for (StructureNode child : source.getChildren()) {
			b.addChild(copyOf(child));
		}
		return b.build();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Optional<String> getTypeName() {
		return Optional.ofNullable(typeName);
	}

	@Override
	public StructureNodeKind getKind() {
		return kind;
	}

	@Override
	public Optional<Object> getValue() {
		return Optional.ofNullable(value);
	}

	@Override
	public List<StructureNode> getChildren() {
		return children;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Optional<ByteRange> getSourceRange() {
		return Optional.ofNullable(sourceRange);
	}

	@Override
	public String toString() {
		return StructureNodePrinter.print(this);
	}

	public static final class Builder {
		private final String name;
		private final StructureNodeKind kind;
		private String typeName;
		private Object value;
		private final List<StructureNode> children = new ArrayList<>();
		private final Map<String, Object> attributes = new LinkedHashMap<>();
		private ByteRange sourceRange;

		private Builder(String name, StructureNodeKind kind) {
			this.name = name;
			this.kind = kind;
		}

		public Builder typeName(String typeName) {
			this.typeName = typeName;
			return this;
		}

		public Builder value(Object value) {
			this.value = value;
			return this;
		}

		public Builder addChild(StructureNode child) {
			this.children.add(Objects.requireNonNull(child));
			return this;
		}

		public Builder addChildren(List<? extends StructureNode> childList) {
			this.children.addAll(childList);
			return this;
		}

		public Builder attribute(String key, Object value) {
			this.attributes.put(key, value);
			return this;
		}

		public Builder attributes(Map<String, ?> attrs) {
			this.attributes.putAll(attrs);
			return this;
		}

		public Builder sourceRange(ByteRange range) {
			this.sourceRange = range;
			return this;
		}

		public DefaultStructureNode build() {
			return new DefaultStructureNode(this);
		}
	}
}
