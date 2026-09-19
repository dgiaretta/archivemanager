package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.AccessRightsInformationRefImpl;

/**
 * The information that identifies the access restrictions pertaining to the
 * Content Data Object, including the legal framework, licensing terms, and access
 * control. It contains the access and distribution conditions stated within the
 * Submission Agreement, related to both preservation (by the OAIS) and final
 * usage (by the Consumer). It also includes the specifications for the
 * application of rights enforcement measures. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = AccessRightsInformationRefImpl.class)
public interface AccessRightsInformation extends InformationObject {

}