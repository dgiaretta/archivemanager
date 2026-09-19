package info.oais.infomodel.interfaces.utility;
/**
 * Timestamp for events
 *
 */
public interface OaisIfTimeStamp extends Comparable<OaisIfTimeStamp>{

	/**
	 * Returns the number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 * @return The number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 */
	public long getTime();

	/**
	 * Set the number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 * @param ms The number of milliseconds since January 1, 1970, 00:00:00 GMT.
	 */
	public void setTime(long ms);

	/**
	 * Compare the time stamps.
	 * @param ts TimeStamp to compare.
	 * @return returns a negative integer, zero, or a positive integer
	 *         as this object is less than, equal to, or greater than the specified time.
	 */
	@Override
	public int compareTo(OaisIfTimeStamp ts);
}
