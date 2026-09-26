/**
 *
 */
package info.oais.infomodel.implementation;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;


import info.oais.infomodel.implementation.representationinformation.ArchivalInformationPackage;
import info.oais.infomodel.interfaces.*;

/**
 * Archival Information Package implementation
 *
 * This complements the basic InformationPackage this has:
 * <ul>
 * <li>PreservationDescriptionInformation</li>
 * <li>IsComplete - declares whether or not the AIP is complete</li>
 * </ul>
 *
 * @author david
 *
 */


// DG20240217 @JsonIgnoreProperties(value = { "contentInformation" })
@JsonPropertyOrder({"PackageVersion", "PackageType", "IsComplete", "PackageDescription", "PackagingInformation","InformationObject", "InformationObject", "PreservationDescriptionInformation"  })
// DG20240217 @JsonRootName(value = "InformationPackage" )
//@JsonProperty("ArchivalInformationPackageRefImpl")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchivalInformationPackageRefImpl extends InformationPackageRefImpl implements ArchivalInformationPackage {

	/**
	 * IsComplete declares whether the AIP is complete
	 */
	Boolean m_IsComplete = false;
	/**
	 * The PDI of the AIP
	 */
	PreservationDescriptionInformation m_PDI = null;

	/**
	 * Create new AIP with no arguments
	 *
	 *
	 */
	public ArchivalInformationPackageRefImpl() {


		super( );
    	/**
    	 * Register the deserializer
    	 */
    	//SimpleModule deserialization = new SimpleModule();
    	//deserialization.addDeserializer(ArchivalInformationPackage.class, new ArchivalInformationPackageDeserialize());

    	//ObjectMapper objectMapper = new ObjectMapper();
    	//objectMapper.registerModule(deserialization);

	}

	/**
	 * Create new AIP
	 *
	 * @param io InformationObject for the AIP
	 * @param pd    Package Description
	 * @param pt	Package type e.g. "General", "AIP", "Query", "Response" etc TODO - ENUM
	 * @param id	IdentifierObject for the AIP
	 * @param pdi	PDI for the AIP;
	 * @param complete Is the package complete
	 *
	 */
	public ArchivalInformationPackageRefImpl(InformationObject io, PackageDescription pd, String pt, IdentifierObject id, PreservationDescriptionInformation pdi, boolean complete) {

		super( io,  pd, "AIP", id, pdi, complete);

	}

	@Override
	public Boolean isDeclaredComplete() {
		return m_IsComplete;
	}
	@Override
	public void setIsDeclaredComplete(Boolean complete) {
		m_IsComplete = complete;
	}
	@Override
	public PreservationDescriptionInformation getPDI() {
		return m_PDI;
	}
	@Override
	public void setPDI(PreservationDescriptionInformation pdi) {
		m_PDI = pdi;
	}
	@Override
	public InformationObject getContentInformation() {
		return m_IO;
	}
	@Override
	public void setContentInformation(InformationObject ci) {
		m_IO = ci;
	}
	

}
