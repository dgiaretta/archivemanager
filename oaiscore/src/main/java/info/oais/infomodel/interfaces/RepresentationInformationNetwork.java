package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.RepresentationInformationNetworkRefImpl;
import info.oais.infomodel.interfaces.representationinformation.AcyclicDirectedGraph;

/**
 * The set of Representation Information that fully describes the meaning of a
 * Data Object. Representation Information in digital forms needs additional
 * Representation Information so its digital forms can be understood over the Long
 * Term. It is a type of Information Object. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = RepresentationInformationNetworkRefImpl.class)
public interface RepresentationInformationNetwork extends RepresentationInformation, AcyclicDirectedGraph {


}