package info.oais.infomodel.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One node in the tree produced by applying a Structure Representation
 * Information to a Data Object.
 *
 * <p>[OAIS] Structure Representation Information: "The Representation
 * Information that imparts information about the arrangement of and the
 * organization of the parts or elements of the Data Object. ... maps bit
 * streams to common computer types such as characters, numbers, and pixels
 * and aggregations of those types such as character strings and arrays."</p>
 *
 * <p>Whichever engine performs that mapping - a DRB node tree, a Kaitai
 * Struct generated parser's object graph, or a parsed Apache Daffodil (DFDL)
 * infoset - the result is always, at bottom, a labelled tree of typed
 * values. {@code StructureNode} is the common shape every engine-specific
 * adapter in this project is required to produce, so code above this layer
 * (rendering, further Semantic Representation Information, validation,
 * spot-checking fixity on individual parsed fields, and so on) never needs
 * to know which engine did the parsing. It is intentionally a read-only,
 * navigation-only view: producing a {@code StructureNode} tree is the job of
 * {@link ExecutableStructureRepInfo}, not of this interface.</p>
 *
 * <p>Implementations are typically thin, engine-specific wrappers held by an
 * {@link ExecutableStructureRepInfo} adapter (see the {@code oais-structure-dfdl},
 * {@code oais-structure-kaitai} and {@code oais-structure-drb} modules), but
 * {@link DefaultStructureNode} is provided for hand-built trees, fixtures and
 * tests.</p>
 */
public interface StructureNode {

	/**
	 * Local name of this node: an element/field name for a COMPOSITE, or an
	 * index rendered as text (e.g. {@code "0"}, {@code "1"}) for a member of
	 * an ARRAY node's children.
	 *
	 * @return the name, never {@code null}
	 */
	String getName();

	/**
	 * The type this node's value was decoded as, if the engine can say -
	 * e.g. a DFDL simple type's local name, a Kaitai type name, a Java
	 * class's simple name. Purely informational.
	 *
	 * @return the type name, or {@link Optional#empty()} if unknown
	 */
	Optional<String> getTypeName();

	/**
	 * @return whether this node is a {@link StructureNodeKind#COMPOSITE},
	 *         {@link StructureNodeKind#ARRAY} or {@link StructureNodeKind#LEAF}
	 */
	StructureNodeKind getKind();

	/**
	 * The decoded value of a LEAF node - typically a {@link String}, a boxed
	 * numeric type, a {@link Boolean}, or a {@code byte[]} for undecoded
	 * binary payloads. Always empty for COMPOSITE and ARRAY nodes.
	 *
	 * @return the value, or {@link Optional#empty()} for a non-leaf node or
	 *         a leaf whose value is legitimately absent (e.g. DFDL
	 *         {@code xsi:nil})
	 */
	Optional<Object> getValue();

	/**
	 * Ordered child nodes; empty for LEAF nodes. Order matters: it reflects
	 * the byte order of the underlying Digital Object, not any subsequent
	 * re-sorting.
	 *
	 * @return the children, never {@code null}, empty for a LEAF
	 */
	List<StructureNode> getChildren();

	/**
	 * Metadata some engines expose alongside a node in addition to its
	 * value/children - DRB node attributes, DFDL {@code dfdl:...} annotation
	 * values surfaced by the engine, and similar. Most nodes will have none.
	 *
	 * @return the attributes, never {@code null}, possibly empty
	 */
	Map<String, Object> getAttributes();

	/**
	 * Where in the original Digital Object this node was decoded from, if
	 * the engine tracked it.
	 *
	 * @return the source range, or {@link Optional#empty()} if the engine
	 *         does not expose provenance at this granularity
	 */
	Optional<ByteRange> getSourceRange();

	/**
	 * Convenience: the first direct child with the given name.
	 *
	 * @param name child name to look for
	 * @return the first matching child, or {@link Optional#empty()}
	 */
	default Optional<StructureNode> child(String name) {
		for (StructureNode c : getChildren()) {
			if (c.getName().equals(name)) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	/**
	 * Convenience: all direct children with the given name, in order. Useful
	 * for engines (DFDL, DRB) that represent a repeated element as several
	 * same-named siblings under a COMPOSITE parent rather than as a single
	 * ARRAY node.
	 *
	 * @param name child name to look for
	 * @return the matching children, in document order, never {@code null}
	 */
	default List<StructureNode> childrenNamed(String name) {
		List<StructureNode> result = new ArrayList<>();
		for (StructureNode c : getChildren()) {
			if (c.getName().equals(name)) {
				result.add(c);
			}
		}
		return result;
	}

	/**
	 * Convenience: the leaf value at a dotted/child path from this node, e.g.
	 * {@code valueAt("header", "labelLen")}.
	 *
	 * @param names the sequence of child names to descend through
	 * @return the leaf value at that path, or {@link Optional#empty()} if
	 *         any step of the path is missing
	 */
	default Optional<Object> valueAt(String... names) {
		StructureNode current = this;
		for (String name : names) {
			Optional<StructureNode> next = current.child(name);
			if (next.isEmpty()) {
				return Optional.empty();
			}
			current = next.get();
		}
		return current.getValue();
	}
}
