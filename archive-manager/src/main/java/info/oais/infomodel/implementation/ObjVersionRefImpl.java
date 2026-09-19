/**
 *
 */
package info.oais.infomodel.implementation;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

import info.oais.infomodel.interfaces.ObjVersion;

/**
 * The version associated with an Object.
 *
 * One version can be compared to another to decide which is the later
 *
 */
//DG XXXX
@JsonIgnoreType
public class ObjVersionRefImpl implements ObjVersion {
    //Getting the current date
    Date date = new Date();
    //This method returns the time in millis
    long timeMilli = date.getTime();
    //Set the default value as the current time as milliseconds
	String m_Version = String.valueOf(timeMilli);

	/**
	 * An int value: 0 if the string is equal to the other string.
	 * &lt; 0 if the string is lexicographically less than the other string
	 * &gt; 0 if the string is lexicographically greater than the other string (more characters)
	 */
	public int compareTo(ObjVersion o) {
		return this.getObjVersionString().compareTo(o.toString());
	}

	/**
	 * Set ObjVersion as a String
	 * @param ver Version as a String
	 *
	 */
	public void setObjVersionString(String ver) {
		m_Version = ver;
	}

	/**
	 * Get ObjVersion as a String
	 * @return Version as a String
	 *
	 */
	public String getObjVersionString() {
		// TODO Auto-generated method stub
		return m_Version;
	}

	/**
	 * Get ObjVersion as a String
	 * @return Version as a String
	 *
	 */
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return m_Version;
	}
}
