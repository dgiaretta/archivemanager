package info.oais.infomodel.interfaces.representationinformation;


/**
 * A classification of RepInfo to facilitate locating the kind of RepInfo wanted.
 * Currently just a String but may add other params and methods.
 *
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
public interface RepInfoCategory {

	/**Returns the category as a String.
	 *
	 * @return The category as a String.
	 */
	public String getCategory();

	/**
	 * Set the category as a String.
	 *
	 * @param cat The category as a String.
	 */
	public void setCategory(String cat);

}