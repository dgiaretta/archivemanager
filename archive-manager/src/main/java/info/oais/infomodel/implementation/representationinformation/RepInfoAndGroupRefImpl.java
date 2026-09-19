package info.oais.infomodel.implementation.representationinformation;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoAndGroup;

public class RepInfoAndGroupRefImpl extends RepInfoGroupRefImpl implements RepInfoAndGroup {

	/**
	 * Constructor
	 */
	public RepInfoAndGroupRefImpl(ArrayList<RepresentationInformation> group) {
		m_Group = group;
	}

	/**
	 * Get the InfoGroup for the vertex.
	 *
	 * @return The array of Info in the Group
	 */
	@Override
	@JsonProperty("RepInfoAndGroup")
	public ArrayList<RepresentationInformation> getGroup(){
		return m_Group;
	}

	/**
	 * Set the members of Info in this group.
	 *
	 * @param group An ArrayList of RepInfo
	 */
	public void setGroup(ArrayList<RepresentationInformation> group) {
		m_Group = group;
	}
}
