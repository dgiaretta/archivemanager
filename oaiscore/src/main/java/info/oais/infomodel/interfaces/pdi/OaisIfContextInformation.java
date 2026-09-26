package info.oais.infomodel.interfaces.pdi;

import info.oais.infomodel.interfaces.ContextInformation;

/**
 * Interface to access Context Information
 */
public interface OaisIfContextInformation extends ContextInformation {

	/**
	 * Get the string which describes the context.
	 *
	 * @return The string which describes the context.
	 */
	public String getContextText();

	/**
	 * Set the string which describes the context.
	 *
	 * @param cs The string which describes the context.
	 */
	public void setContextText(String cs);

}
