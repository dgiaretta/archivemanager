/**
 *
 */
package info.oais.infomodel.implementation.utility;

import java.time.Instant;

import info.oais.infomodel.interfaces.utility.OaisIfTimeStamp;

/**
 *
 */
public class OaisIfTimeStampRefImpl implements OaisIfTimeStamp {

	/**
	 * m_timeStamp Private value of the timeStamp
	 */
	private long m_timeStamp = 0;

	/**
	 * Get the long value of the timestamp
	 * @return The value of the time stamp.
	 */
	public long getTime() {

		return m_timeStamp;
	}

	/**
	 * Set the value of the time stamp
	 * @param ms The value of the time stamp in ms
	 */
	public void setTime(long ms) {
		m_timeStamp = ms;

	}

	/**
	 * Compare the time stamps.
	 * @param ts TimeStamp to compare.
	 * @return returns a negative integer, zero, or a positive integer
	 *         as this object is less than, equal to, or greater than the specified time.
	 */
	public int compareTo(OaisIfTimeStamp ts) {

		long diff = ts.getTime() - m_timeStamp;
		if (diff > 0) {
			return 1;
		} else if (diff == 0) {
			return 0;
		} else {
			return -1;
		}
	}

	/**
	 * A human-readable, unambiguous rendering of this time stamp - an
	 * ISO-8601 date and time in UTC (e.g. {@code "2023-11-14T22:13:20Z"}),
	 * via {@link Instant#ofEpochMilli(long)}. Used wherever this value ends
	 * up displayed as text without any more specific formatting applied -
	 * e.g. a {@link javax.swing.JTable} cell (whose default renderer calls
	 * {@code toString()} on a value with no registered renderer of its own)
	 * or a CSV export - rather than the epoch-millisecond number or the
	 * default {@code Object.toString()}.
	 */
	@Override
	public String toString() {
		return Instant.ofEpochMilli(m_timeStamp).toString();
	}

}
