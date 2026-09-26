package info.oais.infomodel.implementation.representationinformation;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoOrGroup;

public class RepInfoOrGroupRefImpl extends RepInfoGroupRefImpl implements RepInfoOrGroup {

	/**
	 * Constructor
	 */
	public RepInfoOrGroupRefImpl(ArrayList<RepresentationInformation> group) {
		m_Group = group;
	}

//	/**
//	 * Get the InfoGroup for the vertex.
//	 *
//	 * @return The array of Info in the Group
//	 */
//	@JsonProperty("RepInfoOrGroupTemp")
//	public ArrayList<RepresentationInformation> getGroup(){
//		return m_Group;
//	}

	/**
	 * Get the InfoGroup for the vertex.
	 *
	 * @return The array of Info in the Group
	 */
	@JsonProperty("RepInfoOrGroup")
	@JsonRawValue
	public String getJsonGroup(){
		String jsonGroup = "[{\"RepresentationInformation\":";
		System.out.println("Number in OrGroup is " + m_Group.size());
		ObjectMapper groupMapper = new ObjectMapper();
		groupMapper.setSerializationInclusion(Include.NON_NULL);
		try {
			for (int i = 0; i < m_Group.size(); i++) {
				System.out.println("Entry " + i);
				jsonGroup =  jsonGroup + groupMapper.writeValueAsString(m_Group.get(i)) ;
				if (i < m_Group.size() - 1) {
					jsonGroup = jsonGroup + ",\"RepresentationInformation\":";
				}
				System.out.println(jsonGroup);
			}
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		jsonGroup = jsonGroup + "}]";
		System.out.println("JSON for OrGroup is:" + jsonGroup);
		return jsonGroup;
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
