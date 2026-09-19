/**
 *
 */
package info.oais.infomodel.implementation.utility;

import java.util.ArrayList;

import javax.swing.table.DefaultTableModel;

import info.oais.infomodel.interfaces.utility.OaisIfTable;

/**
 * Implementation of OaisIfTable
 */
public class OaisIfTableRefImpl implements OaisIfTable {

	/**
	 * DefaultTableModel private data
	 *
	 */
	private DefaultTableModel m_tableModel = null;
	private ArrayList<Class<?>> m_colType = new ArrayList<>();
	private ArrayList<String> m_colName = new ArrayList<>();

	/**
	 * Zero argument constructor
	 *
	 */
	OaisIfTableRefImpl(){
		/**
		 * Create the empty m_tableModel. Columns, rows and data can be added using accessors.
		 *
		 */
		m_tableModel = new DefaultTableModel();
	}
	/**
	 * Returns the number of rows in this data table.
	 * @return the number of rows in this data table.
	 */
	public long getRowCount() {

		return m_tableModel.getRowCount();
	}

	/**
	 * Returns the number of columns in this data table.
	 * @return the number of columns in this data table.
	 */
	public int getColumnCount() {

		assert m_tableModel.getColumnCount() == m_colName.size() : "Number of column mismatch";

		return m_tableModel.getColumnCount();
	}

	/**
	 * Returns the column name.
	 * @param columnIndex The index of the column for which the name is wanted
	 * @return the name of the column
	 */
	public String getColumnName(int columnIndex) {

		assert m_tableModel.getColumnName(columnIndex).equals(m_colName.get(columnIndex)) : "Name of column mismatch";
		return m_tableModel.getColumnName(columnIndex);
	}

	/**
	 * Returns the most specific superclass for all the cell values in the column.
	 * @param columnIndex The index of the column
	 * @return the most specific superclass for all the cell values in the column.
	 */
	public Class<?> getColumnClass(int columnIndex) {
		/*
		 * Deliberately returns m_colType.get(columnIndex), not
		 * m_tableModel.getColumnClass(columnIndex): javax.swing.table.
		 * DefaultTableModel.getColumnClass always returns Object.class
		 * unless overridden, so it never reflects the type actually passed
		 * to addColumn(...). m_colType is this class's own record of that.
		 */
		return m_colType.get(columnIndex);
	}

	/**
	 * Returns an attribute value for the cell at row and column.
	 * @param rowIndex The index of the row wanted
	 * @param columnIndex The index of the column wanted
	 * @return an attribute value for the cell at row and column.
	 */
	public Object getValueAt(long rowIndex, int columnIndex) {

		return m_tableModel.getValueAt((int) rowIndex, columnIndex);
	}

	/**
	 * Adds a column to the model.
	 * @param colName the name of the new column
	 * @param colType the class of the column
	 *
	 */
	public void addColumn(String colName, Class<?> colType) {
		m_tableModel.addColumn(colName);
		m_colName.add(colName);
		m_colType.add(colType);
	}


	/**
	 * Set the value of a cell
	 * @param rowIndex The index of the row of the cell
	 * @param columnIndex The index of the column of the cell
	 * @param obj The value of the cell - should match the class of the column
	 */
	public void setValueAt(Object obj, long rowIndex, int columnIndex) {
		m_tableModel.setValueAt(obj, (int) rowIndex, columnIndex);

	}

}
