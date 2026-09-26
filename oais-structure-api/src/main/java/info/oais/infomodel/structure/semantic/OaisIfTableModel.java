package info.oais.infomodel.structure.semantic;

import java.util.HashMap;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import info.oais.infomodel.interfaces.utility.OaisIfTable;

/**
 * Adapts any {@link OaisIfTable} - a plain
 * {@link info.oais.infomodel.implementation.utility.OaisIfTableRefImpl}, a
 * {@link StructureNodeBackedTable} built from a {@link TableViewSpecification}
 * or a hand-built {@link TableMapping}, or any other implementation - to
 * Swing's table model API, so it can be displayed (and edited - see below) in
 * a real {@link javax.swing.JTable} without writing per-application adapter
 * code. This is the same interoperability point as the rest of this package:
 * downstream code (here, Swing) only ever needs to know {@code OaisIfTable}.
 *
 * <p><b>Editing:</b> editable by default - {@link #isCellEditable} returns
 * {@code true} unless this model was built via
 * {@link #OaisIfTableModel(OaisIfTable, boolean)} with {@code editable=false}.
 * An edit is <em>never</em> written through to the wrapped {@code OaisIfTable}
 * itself; it is kept in a local overlay only this model sees, coerced (via
 * {@link ValueCoercion}) to the edited column's declared class the same way
 * {@link StructureNodeBackedTable#getValueAt} coerces what it reads. This is
 * deliberate, not a limitation: most of this project's own {@code OaisIfTable}
 * implementations - every {@link StructureNodeBackedTable} and its
 * subclasses - are read-only views over an already-decoded, immutable
 * {@link info.oais.infomodel.structure.StructureNode} tree and throw
 * {@link UnsupportedOperationException} from {@code setValueAt}; routing
 * Swing's edits through a local overlay instead means this model behaves the
 * same way regardless of which kind of {@code OaisIfTable} it wraps, rather
 * than working for some and throwing mid-edit for others. Construct a fresh
 * {@code OaisIfTableModel} (e.g. by re-running the owning
 * {@code ExecutableSemanticRepInfo#apply}) to discard accumulated edits and
 * go back to the wrapped table's own values.</p>
 */
public final class OaisIfTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private final OaisIfTable table;
	private final boolean editable;
	private final Map<RowColumn, Object> edits = new HashMap<>();

	public OaisIfTableModel(OaisIfTable table) {
		this(table, true);
	}

	/**
	 * @param table    the table to adapt
	 * @param editable whether {@link #isCellEditable} reports cells as
	 *                 editable at all; when {@code false}, this model behaves
	 *                 exactly as it did before editing support was added
	 */
	public OaisIfTableModel(OaisIfTable table, boolean editable) {
		this.table = table;
		this.editable = editable;
	}

	@Override
	public int getRowCount() {
		return Math.toIntExact(table.getRowCount());
	}

	@Override
	public int getColumnCount() {
		return table.getColumnCount();
	}

	@Override
	public String getColumnName(int columnIndex) {
		return table.getColumnName(columnIndex);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return table.getColumnClass(columnIndex);
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return editable;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		RowColumn key = new RowColumn(rowIndex, columnIndex);
		if (edits.containsKey(key)) {
			return edits.get(key);
		}
		return table.getValueAt(rowIndex, columnIndex);
	}

	/**
	 * Records an edit in this model's local overlay - see this class's
	 * Javadoc on "Editing". A no-op if this model was built with
	 * {@code editable=false}; {@link #isCellEditable} already tells Swing not
	 * to invoke this in that case, so this check is only a defensive
	 * backstop against a caller invoking it directly.
	 */
	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if (!editable) {
			return;
		}
		Object coerced = ValueCoercion.coerce(aValue, table.getColumnClass(columnIndex));
		edits.put(new RowColumn(rowIndex, columnIndex), coerced);
		fireTableCellUpdated(rowIndex, columnIndex);
	}

	private record RowColumn(int row, int column) {
	}
}
