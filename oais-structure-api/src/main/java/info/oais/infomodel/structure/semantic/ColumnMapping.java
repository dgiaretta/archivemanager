package info.oais.infomodel.structure.semantic;

import java.util.function.Function;

import info.oais.infomodel.structure.StructureNode;

/**
 * One column of a {@link TableMapping}: its name, its declared value class
 * (as {@link info.oais.infomodel.interfaces.utility.OaisIfTable#getColumnClass}
 * reports it), and how to pull that column's value for a given row out of
 * the row's {@link StructureNode}.
 *
 * <p>Written purely against {@link StructureNode}, so the same
 * {@code ColumnMapping} works whether the row came from a DFDL parse, a
 * Kaitai parse, or a DRB node - that is the whole point of this package.</p>
 *
 * @param name        the column's name
 * @param columnClass the column's declared value class
 * @param extractor   given one row node, returns this column's value for
 *                    that row (or {@code null} if absent)
 */
public record ColumnMapping(String name, Class<?> columnClass, Function<StructureNode, Object> extractor) {

	/**
	 * Convenience for the common case: the column's value is the leaf value
	 * of a named direct child of the row node, e.g. {@code row.child("x")}.
	 *
	 * @param name        the column's name, and the row's child name to
	 *                    read it from
	 * @param columnClass the column's declared value class
	 * @return a mapping that reads {@code name}'s value from each row
	 */
	public static ColumnMapping ofChild(String name, Class<?> columnClass) {
		return new ColumnMapping(name, columnClass,
				row -> row.child(name).flatMap(StructureNode::getValue).orElse(null));
	}
}
