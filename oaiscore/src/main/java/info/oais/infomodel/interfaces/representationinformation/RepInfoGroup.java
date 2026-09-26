/**
 *
 */
package info.oais.infomodel.interfaces.representationinformation;

import java.util.ArrayList;

import info.oais.infomodel.interfaces.RepresentationInformation;

/**
 * Group of RepresentationInformation, which includes the associated classification.
 *
 * @author David
 *
 */
public interface RepInfoGroup extends RepresentationInformation {

	/**
	 * Get the Group for the vertex.
	 *
	 * @return The array of RepInfo in the Group
	 */
	public ArrayList<RepresentationInformation> getGroup();

	/**
	 * Set the members of RepInfo in this group.
	 *
	 * @param group An ArrayList of RepInfo
	 */
	public void setGroup(ArrayList<RepresentationInformation> group);

}
