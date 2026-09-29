package info.oais.infomodel.structure.semantic;

import java.util.function.Function;

import info.oais.infomodel.structure.StructureNode;

/**
 * One column of a {@link TableMapping}: its name, its declared value class
 * (as {@link info.oais.infomodel.interfaces.utility.OaisIfTable#getColumnClass}
 * reports it), how to pull that column's value for a given row out of
 * the row's {@link StructureNode}, and optionally what the values mean --
 * their units, a description and an IVOA UCD -- for viewers such as TOPCAT
 * that show column metadata.
 *
 * <p>Written purely against {@link StructureNode}, so the same
 * {@code ColumnMapping} works whether the row came from a DFDL parse, a
 * Kaitai parse, or a DRB node - that is the whole point of this package.</p>
 *
 * @param name        the column's name
 * @param columnClass the column's declared value class
 * @param extractor   given one row node, returns this column's value for
 *                    that row (or {@code null} if absent)
 * @param unit        the values' units (e.g. {@code K}), or {@code null}
 * @param description what the column holds, or {@code null}
 * @param ucd         an IVOA Unified Content Descriptor (e.g. {@code phys.temperature}), or {@code null}
 */
public record ColumnMapping(String name, Class<?> columnClass, Function<StructureNode, Object> extractor,
		String unit, String description, String ucd) {

	/** A column with no units, description or UCD. */
	public ColumnMapping(String name, Class<?> columnClass, Function<StructureNode, Object> extractor) {
		this(name, columnClass, extractor, null, null, null);
	}

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

	/** This column, with the given units, description and UCD wherever it has none of its own. */
	public ColumnMapping withMetadataDefaults(String defaultUnit, String defaultDescription, String defaultUcd) {
		return new ColumnMapping(name, columnClass, extractor, unit != null ? unit : defaultUnit,
				description != null ? description : defaultDescription, ucd != null ? ucd : defaultUcd);
	}
}
