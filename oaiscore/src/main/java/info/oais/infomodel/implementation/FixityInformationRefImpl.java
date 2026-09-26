 /**
 *
 */
package info.oais.infomodel.implementation;

import info.oais.infomodel.interfaces.FixityInformation;

/**
 * @author david
 *
 */
public class FixityInformationRefImpl extends InformationObjectRefImpl implements FixityInformation {

	/**
	 * Constructor for FixityInformation as an Information Object
	 */
	public FixityInformationRefImpl() {
		super();
	}
	/**
	 * Set the String value for the FixityInformation.
	 *
	 * @param fixStr Set the String for the FixityInformation.
	 */
	public FixityInformationRefImpl(String fixStr) {
		super();
		this.setDataObject(new DataObjectRefImpl(fixStr));
	}

	@Override
	public String toString() {
		return "Fixity is: " + getDataObject().toString();
	}

}
