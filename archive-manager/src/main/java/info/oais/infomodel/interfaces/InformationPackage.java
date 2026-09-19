package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.InformationPackageRefImpl;

/**
 * A logical container composed of optional Information Object(s). Associated with
 * this Information Package is Packaging Information used to delimit and identify
 * the Information Object and optional Package Description information used to
 * facilitate searches for the Information Object. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = InformationPackageRefImpl.class)
public interface InformationPackage extends DataObject {

	/**
	 * Return the InformationObject in the package.
	 *
	 * @return The InformationObject which the InformationPackage contains.
	 */
	public InformationObject getInformationObject();

	/**
	 * Set the InformationObject in the InformationPackage.
	 *
	 * @param infoObj The InformationObject for the InformationPackage.
	 */
	public void setInformationObject(InformationObject infoObj);

	/**
	 * Return the optional PreservationDescriptionInformation in the package.
	 *
	 * @return The PreservationDescriptionInformation which the InformationPackage contains.
	 */
	public PreservationDescriptionInformation getPDI();

	/**
	 * Set the PreservationDescriptionInformation in the InformationPackage.
	 *
	 * @param pdiObj The PreservationDescriptionInformation for the InformationPackage.
	 */
	public void setPDI(PreservationDescriptionInformation pdiObj);
	/**
	 * Return the PreservationDescriptionInformation associated with the InformationPackage
	 *
	 * @return the PreservationDescriptionInformation associated with the InformationPackage
	 */
	public PackageDescription getPackageDescription();

	/**
	 * Set the PackageDescription to associate with the InformationPackage.
	 *
	 * @param pd The PackageDescription to associate with the InformationPackage
	 */
	public void setPackageDescription(PackageDescription pd);

	/**
	 * Return the PackagingInformation associated with the InformationPackage
	 *
	 * @return the PackagingInformation associated with the InformationPackage
	 */
	public PackagingInformation getPackagingInformation();

	/**
	 * Set the PackagingInformation to associate with the InformationPackage.
	 *
	 * @param pi The PackagingInformation to associate with the InformationPackage
	 */
	public void setPackagingInformation(PackagingInformation pi);

	/**
	 * Return the PackageType associated with the InformationPackage
	 * "AIP","General","InfoObjectRequest","Query","QueryResponse","ObjectRequestResponse","ErrorResponse"
	 *
	 * @return the PackageType associated with the InformationPackage
	 */
	public String getPackageType();

	/**
	 * Set the PackageType to associate with the InformationPackage.
	 *
	 * @param pt The PackageType to associate with the InformationPackage
	 */
	public void setPackageType(String pt);

	/**
	 * Get the IdentifierObject of the Information Package
	 *
	 * @return IdentifierObject for the Info Package
	 */
	
	@Override
	public IdentifierObject getIdentifierObject();

	/**
	 * Set the IdentifierObject for the Info Package
	 *
	 * @param id IdentifierObject for the Info Package
	 */
	public void setIdentifierObject(IdentifierObject id);

	/**
	 * Get the IdentifierObject of the Object
	 *
	 * @return IdentifierObject for the Object
	 */
	@Override
	public ObjVersion getObjVersion();

	/**
	 * Set the version for the Object
	 *
	 * @param v version for the Object
	 */
	@Override
	public void setObjVersion(ObjVersion v);
	
	/**
	 * Return whether the package is declared to be a complete AIP.
	 *
	 * @return whether the package is declared to be a complete AIP.
	 */
	public boolean getIsDeclaredComplete();

	/**
	 * Set whether the InformationPackage is declared to be a complete AIP.
	 *
	 * @param idc Whether or not the InformationPackage is declared to be a complete AIP.
	 */
	public void setIsDeclaredComplete(boolean idc);
}