/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.UUID;

import info.oais.infomodel.interfaces.*;
import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.InformationObject;
import info.oais.infomodel.interfaces.ObjVersion;
import info.oais.infomodel.interfaces.RepresentationInformation;


/**
 * An OAIS Information Object is a Data Object plus its RepresentationInformation
 *
 * Here we use the DigitalObject, which is a subtype of Data Object
 *
 * @author david
 *
 */
@JsonPropertyOrder({"DataObject", "RepInfo", "IdentifierObject" } )
@JsonRootName(value = "InformationPackage" )
public class InformationObjectRefImpl implements InformationObject {
	DataObject m_DO = null;
	RepresentationInformation m_RI = null;
	IdentifierObject m_ID = null;
	ObjVersion m_ObjVer = new ObjVersionRefImpl();

	public InformationObjectRefImpl() {
		m_DO = null;
		m_RI = null;

		/**
		 * Generate an initial UUID
		 */
		m_ID = new IdentifierObjectRefImpl((UUID.randomUUID()).toString(), "UUID");
	}

	/**
	 * Create InformationObject with specific Data and RepInfo objects
	 *
	 * @param digiObj Data Object for this Information Object
	 * @param repInf  The Representation Information for this Information Object
	 */
	public InformationObjectRefImpl(DataObject digiObj, RepresentationInformation repInf) {
		m_DO = digiObj;
		m_RI = repInf;
		/**
		 * Generate an initial UUID
		 */
		m_ID = new IdentifierObjectRefImpl((UUID.randomUUID()).toString(), "UUID");
	}

	/**
	 * Returns the DataObject of this InformationObject
	 *
	 * @return Data Object for this Information Object
	 */
	@JsonGetter("DataObject")
	public DataObject getDataObject() {
		return m_DO;
	}

	/**
	 * Sets the DigitalObject of this InformationObject
	 *
	 * @param digiObj Digital Object for this Information Object
	 */
	@JsonSetter("DataObject")
	public void setDataObject(DataObject digiObj) {
		m_DO = digiObj;
	}
	/**
	 * Sets the RepresentationInformation of this InformationObject
	 *
	 * @param repInfo RepresentationInformation Object for this Information Object
	 */
	@JsonSetter("RepresentationInformation")
	public void setRepresentationInformation(RepresentationInformation repInfo) {
		m_RI = repInfo;
	}
	/**
	 * Returns the RepresentationInformation of this InformationObject
	 *
	 * @return RepresentationInformation Object for this Information Object
	 */
	@JsonGetter("RepresentationInformation")
	public RepresentationInformation getRepresentationInformation() {
		return m_RI;
	}

	/**
	 * Returns a String
	 * @return  String summarising the RepInfo
	 */
	@JsonIgnore
	public String getString() {
		String str = "DataObject is: ";
		if (m_DO != null) {
			str = str + m_DO.toString();
		} else {
			str = str + "null";
		}

		str = str + ", RepInfo is: ";
		if (m_RI != null) {
			str = str + m_RI.toString();
		} else {
			str = str + "null";
		}

		str = str + ", IdentifierObject is: ";
		if (m_ID != null) {
			str = str + m_ID.toString();
		} else {
			str = str + "null";
		}

		return str;
	}

//	/**
//	 * Set the DataObject as a String
//	 *
//	 * @param desc The String to use as the DataObject
//	 */
//	public void setString(String desc) {
//		this.setDataObject(new DataObjectRefImpl((Object)desc));
//
//	}

	/**
	 * Return the IdentifierObject of the IO
	 *
	 */
	public IdentifierObject getIdentifier() {
		return m_ID;
	}

	/**
	 * Set the IdentifierObject for the IO
	 *
	 * @param id The IdentifierObject to set
	 */
	public void setIdentifier(IdentifierObject id) {
		m_ID = id;
	}

	/**
	 * Return the version of the IO
	 *
	 * @return version
	 */
	public ObjVersion getObjVersion() {

		return m_ObjVer;
	}

	/**
	 * Set the version for the IO
	 *
	 * @param ver The version to set
	 */
	public void setObjVersion(ObjVersion ver) {
		m_ObjVer = ver;
	}
}
