package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.PreservationDescriptionInformationRefImpl;

/**
 * The information, which along with Representation Information, is
 * necessary for adequate preservation of the Content Data Object and which can be
 * categorized as Provenance Information, Context Information, Reference
 * Information, Fixity Information, and Access Rights Information. It is a type of
 * Information Object. NOTE Defining PDI (as well as its components: Provenance
 * Information, Context Information, Reference Information, Fixity Information,
 * and Access Rights Information) as relevant to the Content Data Object does not
 * mean that those concerns are any less important for other data objects or at
 * other levels, for example, it is important to apply reference, fixity,
 * provenance, context and access rights to Representation Information, or to any
 * other information the Archive is preserving. Definition of these terms as
 * relevant to the Content Data Object is simply to ease discussion of these
 * concepts at the Content Data Object level.  [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = PreservationDescriptionInformationRefImpl.class)
public interface PreservationDescriptionInformation extends InformationObject  {

	/**
	 * Get the AccessInformation in the PDI.
	 *
	 * @return The AccessRightsInformation in the PDI.
	 */
	public AccessRightsInformation getAccessRightsInformation();

	/**
	 * Set the AccessRightsInformation in the PDI.
	 *
	 * @param ari The AccessRightsInformation to set in the PDI.
	 */
	public void setAccessRightsInformation(AccessRightsInformation ari);

	/**
	 * Get the ContextInformation in the PDI.
	 *
	 * @return The ContextInformation in the PDI.
	 */
	public ContextInformation getContextInformation();

	/**
	 * Set the ContextInformation in the PDI.
	 *
	 * @param ari The ContextInformation to set in the PDI.
	 */
	public void setContextInformation(ContextInformation ari);

	/**
	 * Get the FixityInformation in the PDI.
	 *
	 * @return The FixityInformation in the PDI.
	 */
	public FixityInformation getFixityInformation();

	/**
	 * Set the FixityInformation in the PDI.
	 *
	 * @param fi The FixityInformation to set in the PDI.
	 */
	public void setFixityInformation(FixityInformation fi);

	/**
	 * Get the ProvenanceInformation in the PDI.
	 *
	 * @return The ProvenanceInformation in the PDI.
	 */
	public ProvenanceInformation getProvenanceInformation();

	/**
	 * Set the ProvenanceInformation in the PDI.
	 *
	 * @param pi The ProvenanceInformation to set in the PDI.
	 */
	public void setProvenanceInformation(ProvenanceInformation pi);

	/**
	 * Get the ReferenceInformation in the PDI.
	 *
	 * @return The ReferenceInformation in the PDI.
	 */
	public ReferenceInformation getReferenceInformation();

	/**
	 * Set the ReferenceInformation in the PDI.
	 *
	 * @param ri The ReferenceInformation to set in the PDI.
	 */
	public void setReferenceInformation(ReferenceInformation ri);


}