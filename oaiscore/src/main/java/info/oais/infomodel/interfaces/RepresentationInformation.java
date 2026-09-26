package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.RepresentationInformationRefImpl;
import info.oais.infomodel.interfaces.representationinformation.RepInfoCategory;

/**
 * The information that maps a Data Object into more meaningful concepts so that
 * the Data Object may be understood in ways exemplified by Preservation
 * Objectives. It is a type of Information Object. NOTE - An example of
 * Representation Information for a bit sequence which is a FITS file might
 * consist of the FITS standard which defines the format plus a dictionary which
 * defines the meaning in the file of keywords which are not part of the standard.
 * This would then allow the information in the FITS file to be used by a computer
 * program to display the image which may be contained in the FITS file, together
 * with the associated coordinate system so that a human can identify objects of
 * interest, for example stars or galaxies. Alternatively, the computer program
 * may identify such objects automatically.
 *
 * NOTE: the RepInfoCategory contains a String e.g. "Structure", "Semantic" or "Other",
 * with addition refinements separated by "/". For example<br/>
 * <ul>
 * <li>Semantic/DataDictionary</li>
 * <li>Structure/BitLevel</li>
 * </ul>
 *
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = RepresentationInformationRefImpl.class)
public interface RepresentationInformation extends InformationObject {

	/**
	 * Get the Category associated with this RepInfo.
	 *
	 * @return The Category of the RepInfo
	 */
	public RepInfoCategory getCategory();

	/**
	 * Put the Category associated with this RepInfo.
	 *
	 * @param cat The Category of the RepInfo
	 */
	public void setCategory(RepInfoCategory cat);

	/**
	 * Get the SemanticRepInfo component
	 *
	 * @return The SemanticRepInfo
	 */
	public RepresentationInformation getSemanticRepInfo();

	/**
	 * Get the StructureRepInfo component
	 *
	 * @return The StructureRepInfo
	 */
	public RepresentationInformation getStructureRepInfo();

	/**
	 * Get the OtherRepInfo component
	 *
	 * @return The OtherRepInfo
	 */
	public RepresentationInformation getOtherRepInfo();

	/**
	 * Put the SemanticRepInfo component
	 *
	 * @param semRI The SemanticRepInfo
	 */
	public void putSemanticRepInfo(RepresentationInformation semRI);

	/**
	 * Put the StructureRepInfo component
	 *
	 * @param strRI The StructureRepInfo
	 */
	public void putStructureRepInfo(RepresentationInformation strRI);

	/**
	 * Put the OtherRepInfo component
	 *
	 * @param otherRI The OtherRepInfo
	 */
	public void putOtherRepInfo(RepresentationInformation otherRI);


}