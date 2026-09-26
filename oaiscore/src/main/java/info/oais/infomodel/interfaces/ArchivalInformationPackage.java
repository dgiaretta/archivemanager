package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.ArchivalInformationPackageRefImpl;

/**
 * An Information Package, consisting of the Content Information and the
 * associated Preservation Description Information (PDI), which is preserved
 * within an OAIS. [OAIS]
 *
 * <p><b>NOTE: </b>The get/set "isDeclaredComplete" has been added because otherwise there is no way to be
 *          sure whether an AIP is ready i.e. has everything needed for preservation.
 *          It should be set as true when the construction of the AIP is considered to finished.</p>
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = ArchivalInformationPackageRefImpl.class)
public interface ArchivalInformationPackage extends InformationPackage {

	/**
	 * If an archive declares that the AIP is complete i.e. is suitable for long term
	 * preservation in that archive, then this method should return TRUE.
	 *
	 * @return A boolean which if true declares that the AIP is complete, otherwise it is incomplete.
	 */
	public Boolean isDeclaredComplete();

	/**
	 * Set whether the AIP is complete.
	 *
	 * @param complete If true then this declares that the AIP is complete i.e.
	 *        has all the components needed for the preservation of the InformationObject
	 *        which is the target of preservation.
	 */
	public void setIsDeclaredComplete(Boolean complete);

	/**
	 * Get the ProvenanceDescriptionInformation in the AIP.
	 *
	 * @return The PDI in the AIP
	 */
	public PreservationDescriptionInformation getPDI();

	/**
	 * @param pdi The ProvenanceDescriptionInformation to be in the AIP
	 */
	public void setPDI(PreservationDescriptionInformation pdi);

	/**
	 * Get the ContentInformation in the AIP.
	 *
	 * @return The ContentInformation in the AIP
	 */
	public InformationObject getContentInformation();

	/**
	 * @param ci The ContentInformation to be in the AIP
	 */
	public void setContentInformation(InformationObject ci);


}