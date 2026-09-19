package info.oais.infomodel.interfaces.pdi;

import info.oais.infomodel.interfaces.AccessRightsInformation;

/**
 * Interface to access Access Rights Information
 */
public interface OaisIfAccessRightsInformation extends AccessRightsInformation {

	/**
	 * Get the string which describes the access rights.
	 *
	 * @return The string which describes the access rights.
	 */
	public String getAccessRightsText();

	/**
	 * Set the string which describes the access rights.
	 *
	 * @param art The string which describes the access rights.
	 */
	public void setAccessRightsText(String art);
}
