/**
 *
 */
package info.oais.infomodel.implementation;

import com.fasterxml.jackson.annotation.JsonIgnore;

import info.oais.infomodel.interfaces.ContextInformation;

/**
 * Context Information implementation
 * @author david
 *
 */
public class ContextInformationRefImpl extends InformationObjectRefImpl implements ContextInformation {

	/**
	 * Constructor for the ContextInformation.
	 *
	 */
	public ContextInformationRefImpl() {
		super();
	}
	/**
	 * Set the String value for the ContextInformation.
	 *
	 * @param contextStr String for the ContextInformation.
	 */
	public ContextInformationRefImpl(String contextStr) {
		super();
		this.setDataObject(new DataObjectRefImpl(contextStr));
	}

	/**
	 * Get the String value for the ContextInformation.
	 *
	 * @return String for the ContextInformation.
	 */
	@Override
	@JsonIgnore
	public String getString() {
		return getDataObject().toString();
	}

	/**
	 * Set the String value for the ContextInformation.
	 *
	 * @param desc String for the ContextInformation.
	 */
	public void setString(String desc) {
		this.setDataObject(new DataObjectRefImpl(desc));

	}

}
