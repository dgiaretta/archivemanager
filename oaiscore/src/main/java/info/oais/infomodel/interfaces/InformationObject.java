package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.InformationObjectRefImpl;

/**
 * An Information Object is a Data Object together with its Representation Information [OAIS]
 *
 * <b>Information</b>: Any type of knowledge that can be exchanged. In an
 * exchange, it is represented by data. <br/> NOTE - An example of Information is
 * a string of bits (the data) accompanied by a description of how to interpret
 * the string of bits as numbers representing temperature observations measured in
 * degrees Celsius (the Representation Information).  [OAIS]<br/>
 * The InformationObject may contain the DataObject and its RepresentationInformation,
 * or may contain an IdentifierObject which points to these.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = InformationObjectRefImpl.class)
public interface InformationObject  {

	/**
	 * Get the DataObject of the InformationObject.
	 * If the DataObject is an instance of an IdentifierObject then it
	 * points to the actual Data Object
	 *
	 * @return The DataObject of the InformationObject.
	 */
	public DataObject getDataObject();
	/**
	 * Get the RepresentationInformation of the informationObject.
	 *
	 * @return The RepInfo of the InformationObject.
	 */
	public RepresentationInformation getRepresentationInformation();

	/**
	 * Set the DataObject of the InformationObject.
	 *
	 * @param dataObj    The DataObject for the InfoObject.
	 */
	public void setDataObject(DataObject dataObj);

	/**
	 * Set the RepresentationInformation of the InformationObject.
	 *
	 * @param repInfo    The RepInfo for the InfoObject.
	 */
	public void setRepresentationInformation(RepresentationInformation repInfo);

	/**
	 * Get the IdentifierObject of the Information Object
	 *
	 * @return IdentifierObject for the Info Object
	 */
	public IdentifierObject getIdentifier();

	/**
	 * Set the IdentifierObject for the Info Object
	 *
	 * @param id IdentifierObject for the Info Object
	 */
	public void setIdentifier(IdentifierObject id);

	/**
	 * Get the IdentifierObject of the Information Object
	 *
	 * @return IdentifierObject for the Info Object
	 */
	public ObjVersion getObjVersion();

	/**
	 * Set the version for the Info Object
	 *
	 * @param v version for the Info Object
	 */
	public void setObjVersion(ObjVersion v);
}