package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.ReferenceInformationRefImpl;

/**
 * The information that is used as an identifier for the Content Data Object. It
 * also includes identifiers that allow outside systems to refer unambiguously to
 * a particular Content Data Object. NOTE - An example of Reference Information is
 * an ISBN. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = ReferenceInformationRefImpl.class)
public interface ReferenceInformation extends InformationObject  {

}