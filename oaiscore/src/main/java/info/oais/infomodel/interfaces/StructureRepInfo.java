package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.StructureRepInfoRefImpl;

/**
 * The Representation Information that imparts information about the arrangement
 * of and the organization of the parts or elements of the Data Object. NOTE - For
 * example, Structure Representation Information maps bit streams to common
 * computer types such as characters, numbers, and pixels and aggregations of
 * those types such as character strings and arrays. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */
@JsonDeserialize(as = StructureRepInfoRefImpl.class)
public interface StructureRepInfo extends RepresentationInformation  {

}