package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.OtherRepInfoRefImpl;

/**
 * A type of Representation Information which cannot easily be classified as
 * Structure Representation Information or Semantic Representation Information. It
 * is a type of Information Object. NOTE - For example, software, algorithms,
 * encryption, written instructions and many other things may be needed to
 * understand the Content Data Object in ways exemplified by the Preservation
 * Objectives, all of which therefore would be, by definition, Representation
 * Information, yet would not obviously be either Structure Representation
 * Information or Semantic Representation. Information defining how the Structure
 * Representation Information and the Semantic Representation Information relate
 * to each other, or software needed to process a database file would also be
 * regarded as Other Representation Information. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = OtherRepInfoRefImpl.class)
public interface OtherRepInfo extends RepresentationInformation  {

}