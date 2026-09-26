package info.oais.infomodel.interfaces.utility;

/**
 * An interface useful for dealing with image information
 *
 */
public interface OaisIfImage {
	/**
	 * Find the number of rows in the image
	 * @return The number of rows in the image
	 */
	public long getRowCount();

	/**
	 * Find the number of columns in the image
	 * @return number of columns in the image
	 */
	public int getColumnCount();

	/**
	 * Get the Class of the pixels in the image
	 *
	 * @return The class of the pixels in the image
	 */
	public Class<?> getPixelClass();

	/**
	 * Get the value of the pixels in the image.
	 *
	 * @return An array with the pixel values of the image
	 */
	public Object[][] getPixelValues();


	/**
	 * Set the number of rows in the image
	 *
	 * @param rowNum The number of rows in the image
	 */
	public void setRowCount(long rowNum);

	/**
	 * Set the number of columns in the image
	 * @param colCount The number of columns in the image
	 */
	public void setColumnCount(int colCount);

	/**
	 * Set the Class of the pixels in the image
	 *
	 * @param pixClass The Class of the pixels
	 *
	 */
	public void setPixelClass(Class<?> pixClass);

	/**
	 * Set the value of the cell at the given row and  specified column in the image.
	 *
	 * @param rowIndex The index (starting at zero) of the row required.
	 * @param columnIndex The index (starting at zero) of the column required.
	 * @param obj The object which is the value in the specified cell.
	 *
	 */
	public void setValueAt(long rowIndex, int columnIndex, Object obj);

	/**
	 * Set the value of the pixels in the image.
	 *
	 * @param pix An array with the pixel values of the image
	 */
	public void setPixelValues(Object[][] pix);


}
