package info.oais.infomodel.interfaces.utility;
/**
 * An interface useful for dealing with tabular information
 *
 */
public interface OaisIfTable {
	/**
	 * Find the number of rows in the table
	 * @return The number of rows in the table, or if this is not know then 0
	 */
	public long getRowCount();

	/**
	 * Find the number of columns in the table
	 * @return number of columns in the table
	 */
	public int getColumnCount();

	/**
	 * Get the names of a specified column in the table
	 *
	 * @param columnIndex The index (starting at zero) of the column required.
	 *
	 * @return The name of the column with the given index (starting at 0)
	 * in the table
	 */
	public String getColumnName(int columnIndex);

	/**
	 * Get the Class of a specified column in the table
	 *
	 * @param columnIndex The index (starting at zero) of the column required.
	 *
	 * @return The class of the column with the given index (starting at 0)
	 * in the table
	 */
	public Class<?> getColumnClass(int columnIndex);

	/**
	 * Get the value of the cell at the given row and  specified column in the table.
	 *
	 * @param rowIndex The index (starting at zero) of the row required.
	 * @param columnIndex The index (starting at zero) of the column required.
	 *
	 * @return The value of the cell with the given index (starting at 0) of row and column.
	 * in the table
	 */
	public Object getValueAt(long rowIndex, int columnIndex);


	/**
	 * Adds a column to the model.
	 * @param colName the name of the new column
	 * @param colType the class of the column
	 *
	 */
	public void addColumn(String colName, Class<?> colType);


	/**
	 * Set the value of a cell
	 * @param rowIndex The index of the row of the cell
	 * @param columnIndex The index of the column of the cell
	 * @param obj The value of the cell - should match the class of the column
	 */
	public void setValueAt(Object obj, long rowIndex, int columnIndex);


}
