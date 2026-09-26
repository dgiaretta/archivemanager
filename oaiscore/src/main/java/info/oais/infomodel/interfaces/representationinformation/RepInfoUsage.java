package info.oais.infomodel.interfaces.representationinformation;

import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.RepresentationInformation;

/**
 * Simple use of RepresentationInformation.
 * @author David
 * @version 1.0
 * @since 06-Sep-2023 15:59:47
 */
public interface RepInfoUsage {

	/**
	 * Get names of applications to use
	 *
	 * @param ri Representation Information
	 * @return array of names of applications which are likely to be able to use the DataObject for which ri is the RepresentationInformation.
	 */
	public String[]  getApplication(RepresentationInformation ri);

	/**
	 * Set the name of applications
	 *
	 * @param ri The Representation Information for which the application may be useful.
	 * @param appNames The name of the applications to try.
	 */
	public void setApplication(RepresentationInformation ri, String[] appNames);

	/**
	 * Execute the named application, inputting the DataObject.
	 * This could be something like displaying an image or document or
	 * playing music. This is essentially a conceptual extension of MediaType
	 * that is used for Web browsers, which ignores Semantics.
	 *
	 * @param appName The name of the application to use
	 * @param dato The DataObject from which to get the bytes to provide to the application.
	 */
	public void executeApp(String appName, DataObject dato);

	/**
	 * Get the classes/interfaces which the Representation Information
	 * implements.
	 *
	 * @param ri The Representation Information for which one is concerned
	 * @return The classes/interfaces which the Representation
	 *         Information implements
	 */

	public Class<?>[] getInterfaces(RepresentationInformation ri);

//	/**
//	 * Set the classes/interfaces which the Representation Information
//	 * implements.
//	 *
//	 * @param repi The Representation Information for which one is concerned
//	 * @param cli The classes/interfaces which the Representation
//	 *         Information implements
//	 */
//
//	public void setInterfaces(RepresentationInformation repi, Class<?>[] cli);
//

}
