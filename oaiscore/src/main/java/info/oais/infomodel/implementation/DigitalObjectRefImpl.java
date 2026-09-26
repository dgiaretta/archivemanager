/**
 *
 */
package info.oais.infomodel.implementation;

//import java.io.Exception;
import java.io.InputStream;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

import info.oais.infomodel.interfaces.DigitalObject;

/**
 * @author david
 *
 */
@JsonPropertyOrder({"DigitalObject", "IdentifierObject", "Size"  })
public class DigitalObjectRefImpl extends DataObjectRefImpl implements DigitalObject {

	/**
	 * Internal size
	 */
	BigInteger m_SIZE = null;

	/**
	 * Create new DigitalObject
	 *
	 */
	public DigitalObjectRefImpl() {
		super();
	}
	/**
	 * Create new DataObject
	 *
	 * @param obj ByteArrayInputStream for the DigitalObject
	 *
	 */
	public DigitalObjectRefImpl(InputStream obj) {

		super(obj);

	}
	@Override
	/**
	 * Potential interface which is pretty close to to OAIS definition
	 *
	 * TODO: should be Set of Sequences e.g.
	 * HashSet<InputStream>
	 *
	 * @return The set of byte sequences which make up the DigitalObject
	 */
	@JsonGetter("DigitalObject")
	public InputStream getObject() {
		return (InputStream) m_DATA;
	}


	 /**
	 * Set the set of byte sequences.
	 *
	 * @param sd The Set of byte sequences which make up the DigitalObject.
	 *
	*/
	@JsonSetter("DigitalObject")
	public void setObject(InputStream sd) {
		m_DATA = sd;
	}


	/**
	 * Get an estimate of the size in bytes of the DataObject
	 *
	 * @return the estimate of the size in bytes  of the DigitalObject.
	 * This allows one to decide whether to download all the bytes.
	 */
	@JsonGetter("Size")
	public BigInteger getSize() {
		/**
		 * TODO get size from InputStream
		 * NOTE that available is an estimate - max size is INT not LONG
		 */
		if (m_DATA == null) {
			return new BigInteger("0");
		} else {
			try {
				return new BigInteger("0"); //((InputStream)m_DATA); //.available();
			} catch (Exception e) {
				e.printStackTrace();
				return new BigInteger("0");
			}
		}

	}

	/**
	 * Set an estimate of the size in bytes of the DataObject
	 *
	 * @param size The estimate of the size in bytes  of the DigitalObject.
	 *
	 */
	@JsonSetter("Size")
	public void setSize(BigInteger size) {
		// TODO Auto-generated method stub
		m_DATA = size;
	}
}
