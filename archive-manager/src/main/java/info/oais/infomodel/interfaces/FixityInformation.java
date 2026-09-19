package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.FixityInformationRefImpl;

/**
 * The information which documents the mechanisms that ensure that the Content
 * Data Object has not been altered in an undocumented manner. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = FixityInformationRefImpl.class)
public interface FixityInformation extends InformationObject {

}