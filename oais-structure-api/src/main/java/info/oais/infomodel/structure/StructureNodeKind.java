package info.oais.infomodel.structure;

/**
 * The shape a {@link StructureNode} takes. Every engine we bridge to - DRB,
 * Kaitai Struct, Apache Daffodil (DFDL), or anything added later - reduces a
 * parsed fragment of a Digital Object to one of these three shapes, even
 * though each engine has its own native vocabulary for them (a DRB node with
 * children, a Kaitai {@code repeat:} field, a DFDL complex/simple element).
 *
 * @author David (design), adapter layer proposed on top of oaisCore
 */
public enum StructureNodeKind {

	/**
	 * A node made up of a fixed, named set of other nodes (a struct/record).
	 * Corresponds to a DFDL complex type element, a non-repeated Kaitai
	 * {@code seq:}/{@code type:} field, or a DRB node with children of
	 * different names.
	 */
	COMPOSITE,

	/**
	 * A node made up of an ordered, homogeneous list of other nodes, indexed
	 * rather than named. Corresponds to a Kaitai {@code repeat:} field. DFDL
	 * and DRB usually surface repetition as several same-named siblings
	 * under a COMPOSITE parent instead; see {@link StructureNode#childrenNamed(String)}.
	 */
	ARRAY,

	/**
	 * A node with a single decoded value and no children: the point at which
	 * Structure Representation Information has finished "mapping bit streams
	 * to common computer types such as characters, numbers, and pixels"
	 * [OAIS].
	 */
	LEAF
}
