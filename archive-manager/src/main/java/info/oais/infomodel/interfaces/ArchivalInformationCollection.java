package info.oais.infomodel.interfaces;

import java.util.ArrayList;

/**
 * An Archival Information Package whose Content Information is an aggregation of
 * other Archival Information Packages.; its PDI must include a description of the
 * collection criteria and process. NOTE - At a minimum all OAISes can be viewed
 * as having at least one AIC which contains all the AIPs held by the OAIS. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
//@JsonDeserialize(as = ArchivalInformationCollectionRefImpl.class)
public interface ArchivalInformationCollection extends ArchivalInformationPackage {

	/**
	 * Get the array of AIPs which the AIC contains.
	 *
	 * @return An array of AIPs which the AIC contains.
	 */
	public ArrayList<ArchivalInformationPackage> getArchivalInformationPackage();

	/**
	 * Set the array of AIPs which the AIC contains.
	 *
	 * @param aips The array of AIPs which the AIC contains
	 */
	public void setArchivalInformationPackage(ArrayList<ArchivalInformationPackage> aips);

	/**
	 * Get the CollectionDescription for the AIC.
	 *
	 * @return The COllectionDescription for the AIC.
	 */
	public CollectionDescription getCollectionDescription();

	/**
	 * Set the CollectionDescription for the AIC.
	 *
	 * @param colldesc The COllectionDescription for the AIC.
	 */
	public void setCollectionDescription(CollectionDescription colldesc);

}