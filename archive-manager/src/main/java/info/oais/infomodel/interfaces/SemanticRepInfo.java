package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.SemanticRepInfoRefImpl;

/**
 * The Representation Information that further describes the meaning of the Data
 * Object, and its parts or elements, beyond that provided by the Structure
 * Representation Information. NOTE - For example, Semantic Representation
 * Information may describe the meaning of columns, and perhaps particular values
 * seen in the columns of a spreadsheet.  [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */
@JsonDeserialize(as = SemanticRepInfoRefImpl.class)
public interface SemanticRepInfo extends RepresentationInformation  {

}