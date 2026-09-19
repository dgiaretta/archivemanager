package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.PackageDescriptionRefImpl;

/**
 * The information intended for use by Access Aids. It is a type of Information
 * Object.  [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = PackageDescriptionRefImpl.class)
public interface PackageDescription extends InformationObject  {

}