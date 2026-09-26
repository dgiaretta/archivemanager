package info.oais.infomodel.implementation.pdi;

import info.oais.infomodel.implementation.ContextInformationRefImpl;
import info.oais.infomodel.interfaces.pdi.OaisIfContextInformation;

/**
 * Context with some convenience methods.
 *
 */
public class OaisIfBasicContextInformationRefImpl extends ContextInformationRefImpl implements OaisIfContextInformation {

	private String m_cont = null;

	/**
	 * Constructor for OaisIfBasicAccessRightsInformationRefImpl
	 *
	 * @param contextStr The text string of Access Rights
	 */
	public OaisIfBasicContextInformationRefImpl(String contextStr) {
		super(contextStr);
		m_cont = contextStr;
	}


	/**
	 * Get Context Text
	 *
	 * @return Context text
	 */
	public String getContextText() {

		return m_cont;
	}

	/**
	 * Set Context Text
	 *
	 * @param contextStr Context text
	 */
	public void setContextText(String contextStr) {
		m_cont = contextStr;

	}

}
