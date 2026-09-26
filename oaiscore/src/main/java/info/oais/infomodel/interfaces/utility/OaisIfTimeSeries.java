package info.oais.infomodel.interfaces.utility;

/**
 * An interface useful for dealing with tabular information
 *
 * Column zero must have class OaisTimeStamp, and is the starting
 * time of the event in this row, and must be non-decreasing.
 * If column 1 also has class OaisTimeStamp then this is the end time of the event.
 * All other columns provide details of the events at this time, or time interval.
 *
 */
public interface OaisIfTimeSeries extends OaisIfTable {



}
