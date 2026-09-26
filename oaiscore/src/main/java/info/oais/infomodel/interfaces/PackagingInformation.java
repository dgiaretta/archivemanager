package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.PackagingInformationRefImpl;

/**
 * The information that describes how the components of an Information Package are
 * logically or physically bound together and how to identify and extract the
 * components. It is a type of Information Object.  [OAIS]
 *
 * <p><b>NOTE: </b> Added "extends RepInfo" so we can use RepInfo APIs.</p>
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = PackagingInformationRefImpl.class)
public interface PackagingInformation extends RepresentationInformation  {//InformationObject, RepresentationInformation  {


}