package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.ContextInformationRefImpl;

/**
 * The information that documents the relationships of the Content Data Object to
 * its environment. This includes why the Content Data Object was created and how
 * it relates to other Content Data Objects. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = ContextInformationRefImpl.class)
public interface ContextInformation extends InformationObject {

}