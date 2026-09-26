package info.oais.infomodel.structure.semantic;

import java.util.List;

import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.StructureNode;

/**
 * A read-only {@link OaisIfTable} view over a list of row {@link StructureNode}s
 * and a {@link ColumnMapping} list - see {@link TableSemanticRepInfo}. Column
 * values are computed on demand from the underlying {@code StructureNode}s,
 * not copied out up front, the same way the DFDL/Kaitai/DRB
 * {@code StructureNode} adapters themselves avoid copying a large parsed
 * structure.
 *
 * <p>Not {@code final}: {@link StructureNodeBackedTimeSeries} and
 * {@link StructureNodeBackedVector} extend this to pick up
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTimeSeries} /
 * {@link info.oais.infomodel.interfaces.utility.OaisIfVector} typing without
 * duplicating any logic - both are plain {@code OaisIfTable}s whose column
 * zero (and, for time series, optionally column one) happens to hold a
 * particular value type by convention, not by a different method set.</p>
 *
 * <p><b>Coercion:</b> a {@link ColumnMapping#extractor()} reads a row's
 * {@link StructureNode#getValue()} as-is, and different {@code StructureNode}
 * adapters hand back different Java types for what is conceptually the same
 * value - e.g. a DFDL adapter with no typed-value information available for
 * an element falls back to the raw DOM text ({@link String}), while a Kaitai
 * or DRB adapter for the equivalent field returns an already-typed
 * {@link Integer}. Rather than let every {@link ColumnMapping} caller repeat
 * defensive casting/parsing, {@link #getValueAt} coerces the extractor's raw
 * result to the column's declared {@link ColumnMapping#columnClass()} via
 * the shared {@link ValueCoercion#coerce}, best-effort: a value that cannot
 * be coerced (an unparsable {@code String}, or a target class it does not
 * know about) is returned unchanged rather than the method throwing,
 * consistent with this package's overall best-effort spirit. The same
 * coercion is applied on the write side too, when an edit made through
 * {@link OaisIfTableModel} needs to match a column's declared class.</p>
 */
class StructureNodeBackedTable implements OaisIfTable {

	private final List<StructureNode> rows;
	private final List<ColumnMapping> columns;

	StructureNodeBackedTable(List<StructureNode> rows, List<ColumnMapping> columns) {
		this.rows = List.copyOf(rows);
		this.columns = columns; // already List.copyOf'd by TableMapping's compact constructor
	}

	@Override
	public long getRowCount() {
		return rows.size();
	}

	@Override
	public int getColumnCount() {
		return columns.size();
	}

	@Override
	public String getColumnName(int columnIndex) {
		return columns.get(columnIndex).name();
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return columns.get(columnIndex).columnClass();
	}

	@Override
	public Object getValueAt(long rowIndex, int columnIndex) {
		StructureNode row = rows.get(Math.toIntExact(rowIndex));
		ColumnMapping column = columns.get(columnIndex);
		return ValueCoercion.coerce(column.extractor().apply(row), column.columnClass());
	}

	@Override
	public void addColumn(String colName, Class<?> colType) {
		throw new UnsupportedOperationException(
				"This table is a read-only view over a StructureNode tree - its columns are fixed by its TableMapping");
	}

	@Override
	public void setValueAt(Object obj, long rowIndex, int columnIndex) {
		throw new UnsupportedOperationException(
				"This table is a read-only view over a StructureNode tree, which is itself read-only");
	}
}
