package info.oais.infomodel.implementation.representationinformation;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoOrGroup;

public class RepInfoOrGroupRefImpl extends RepInfoGroupRefImpl implements RepInfoOrGroup {

	/**
	 * Empty Constructor, e.g. for reading from JSON
	 */
	public RepInfoOrGroupRefImpl() {
		super();
	}

	/**
	 * Constructor
	 */
	public RepInfoOrGroupRefImpl(ArrayList<RepresentationInformation> group) {
		m_Group = group;
	}

	/**
	 * Get the InfoGroup for the vertex: the alternatives, as a JSON array (each member its own object, so
	 * the JSON has no repeated keys).
	 *
	 * @return The array of Info in the Group
	 */
	@Override
	@JsonProperty("RepInfoOrGroup")
	public ArrayList<RepresentationInformation> getGroup(){
		return m_Group;
	}

	/**
	 * Set the members of Info in this group.
	 *
	 * @param group An ArrayList of RepInfo
	 */
	@Override
	@JsonProperty("RepInfoOrGroup")
	public void setGroup(ArrayList<RepresentationInformation> group) {
		m_Group = group;
	}
}
