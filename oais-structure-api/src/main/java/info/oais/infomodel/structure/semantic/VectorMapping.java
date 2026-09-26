package info.oais.infomodel.structure.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import info.oais.infomodel.implementation.utility.OaisIfGeometryRefImpl;
import info.oais.infomodel.interfaces.utility.GeometryKind;
import info.oais.infomodel.interfaces.utility.OaisIfGeometry;
import info.oais.infomodel.structure.StructureNode;

/**
 * Declares how to see some {@link StructureNode} subtree as a vector
 * (feature/geometry) table: like {@link TableMapping}, but column zero is
 * fixed to produce {@link OaisIfGeometry} values, per
 * {@link info.oais.infomodel.interfaces.utility.OaisIfVector}'s documented
 * contract. See {@link VectorSemanticRepInfo}.
 *
 * @param rowSelector      see {@link TableMapping#rowSelector()} - here, one
 *                         row per feature
 * @param geometryColumn   column zero: must produce {@link OaisIfGeometry}
 *                         values
 * @param attributeColumns the remaining feature-attribute columns, in order
 */
public record VectorMapping(Function<StructureNode, List<StructureNode>> rowSelector,
		ColumnMapping geometryColumn,
		List<ColumnMapping> attributeColumns) {

	public VectorMapping {
		attributeColumns = attributeColumns == null ? List.of() : List.copyOf(attributeColumns);
	}

	/**
	 * Convenience: a {@code geometryColumn} {@link ColumnMapping} that reads
	 * a fixed-kind, fixed-dimension geometry whose coordinate tuples come
	 * from a feature row via the given selector - e.g. a POINT feature whose
	 * "x"/"y" children are its one and only coordinate tuple, or a
	 * LINE_STRING feature whose repeated "vertex" children each contribute a
	 * coordinate tuple.
	 *
	 * @param columnName         the geometry column's name
	 * @param kind               the fixed geometry kind every row has
	 * @param coordinateSelector given one feature row node, returns the
	 *                           ordered coordinate tuples (each of length
	 *                           {@code dimension})
	 * @param dimension          2 or 3
	 * @return a mapping producing an {@link OaisIfGeometry} for each row
	 */
	public static ColumnMapping geometryColumn(String columnName, GeometryKind kind,
			Function<StructureNode, double[][]> coordinateSelector, int dimension) {
		return new ColumnMapping(columnName, OaisIfGeometry.class, row -> {
			OaisIfGeometryRefImpl geometry = new OaisIfGeometryRefImpl();
			geometry.setKind(kind);
			geometry.setDimension(dimension);
			geometry.setCoordinates(coordinateSelector.apply(row));
			return geometry;
		});
	}

	TableMapping toTableMapping() {
		List<ColumnMapping> columns = new ArrayList<>();
		columns.add(geometryColumn);
		columns.addAll(attributeColumns);
		return new TableMapping(rowSelector, columns);
	}
}
