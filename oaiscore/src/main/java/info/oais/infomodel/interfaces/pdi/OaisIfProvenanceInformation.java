package info.oais.infomodel.interfaces.pdi;

import info.oais.infomodel.interfaces.ProvenanceInformation;
import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;

/**
 * Interface for accessing Provenance Information.
 * This is an OaisIfTimeSeries with the constraints that there
 * must be a column with names <i>ProvenanceActor</i>,
 * and one with name <i>ProvenanceAction</i>, both with Class String.
 *
 */
public interface OaisIfProvenanceInformation extends ProvenanceInformation, OaisIfTimeSeries{


}
