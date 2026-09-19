package info.oais.infomodel.implementation;

import com.fasterxml.jackson.annotation.JsonIgnore;

import info.oais.infomodel.interfaces.AccessRightsInformation;

/**
 * AccessRights Information Object
 * This class represents the Access Rights Information as an Information Object.
 * It provides methods to set and get the Access Rights Information as a String.
 *
 * Author: David
 */
public class AccessRightsInformationRefImpl extends InformationObjectRefImpl implements AccessRightsInformation {

    /**
     * Default constructor for Access Rights Information.
     */
    public AccessRightsInformationRefImpl() {
        super();
    }

    /**
     * Constructor for Access Rights Information with a String value.
     *
     * @param ariStr String for the Access Rights Information.
     */
    public AccessRightsInformationRefImpl(String ariStr) {
        super();
        this.setDataObject(new DataObjectRefImpl(ariStr));
    }

    /**
     * Get the String value for the Access Rights Information.
     *
     * @return String for the Access Rights Information.
     */
    @Override
	@JsonIgnore
    public String getString() {
        return getDataObject().toString();
    }

    /**
     * Set the String value for the Access Rights Information.
     *
     * @param desc String for the Access Rights Information.
     */
    public void setString(String desc) {
        this.setDataObject(new DataObjectRefImpl(desc));
    }
}