package info.oais.infomodel.interfaces.pdi;

import info.oais.infomodel.interfaces.FixityInformation;

/**
 * Interface to access Fixity Information
 *
 */
public interface OaisIfFixityInformation extends FixityInformation {

	/**
	 * Get the names of the algorithms used to calculate hashes.
	 *
	 * @return STring array with the names of the algorithms used.
	 */
	public String[] getFixityTypes();

	/**
	 * Get  the hash codes
	 *
	 * @return String array with the hash values corresponding to the algorithms.
	 */
	public String[] getFixityCodes();

	/**
	 * Get the description of the mechanisms that ensure
	 * that the Data Object has not been altered in an
	 * undocumented manner.
	 *
	 * @return The String which describes the mechanisms that ensure
	 * that the Data Object has not been altered in an
	 * undocumented manner.
	 */
	public String getFixityProcedures();

	/**
	 * Set the names of the algorithms used to calculate hashes.
	 *
	 * @param ft String array with the names of the algorithms used.
	 */
	public void setFixityTypes(String[] ft);

	/**
	 * Set  the hash codes
	 *
	 * @param fc String array with the hash values corresponding to the algorithms.
	 */
	public void setFixityCodes(String[] fc);

	/**
	 * Set the description of the mechanisms that ensure
	 * that the Data Object has not been altered in an
	 * undocumented manner.
	 *
	 * @param fp The String which describes the mechanisms that ensure
	 * that the Data Object has not been altered in an
	 * undocumented manner.
	 */
	public void setFixityProcedures(String fp);

}
