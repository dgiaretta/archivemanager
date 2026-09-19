/**
 *
 */
package info.oais.infomodel.implementation.utility;

import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;

/**
 * An interface useful for dealing with tabular information
 *
 * Column zero must have class OaisTimeStamp, and is the starting
 * time of the event in this row, and must be non-decreasing.
 * If column 1 also has class OaisTimeStamp then this is the end time of the event.
 * All other columns provide details of the events at this time, or time interval.
 *
 */
public class OaisIfTimeSeriesRefImpl extends OaisIfTableRefImpl implements OaisIfTimeSeries {

	private OaisIfTableRefImpl m_timeSeries = null;
	/**
	 * Constructor
	 * @param duration - if TRUE then create the   second column as a timestamp
	 */
	OaisIfTimeSeriesRefImpl(boolean duration){
		/**
		 * Create the empty m_timeSeries. Columns, rows and data can be added using accessors.
		 * Add the first and second columns
		 */
		m_timeSeries = new OaisIfTableRefImpl();
		/**
		 * Create column 0 as a OaisTimeStamp
		 */
		m_timeSeries.addColumn("EventStart", (new OaisIfTimeStampRefImpl()).getClass() );
		/**
		 * If duration of true then create column 1 as a OaisTimeStamp - EventEnd. If this is not used then we can ignore it.
		 */
		if (duration) {
		    m_timeSeries.addColumn("EventEnd", (new OaisIfTimeStampRefImpl()).getClass() );
		}
	}


}
