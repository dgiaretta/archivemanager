package info.oais.infomodel.interfaces;

import java.io.InputStream;
import java.math.BigInteger;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;


/**
 * An object composed of a set of bit sequences. [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = DigitalObjectRefImpl.class)
public interface DigitalObject extends DataObject {

	/**
	 * Potential interface which is pretty close to to OAIS definition
	 *
	 *
	 * @return The set of byte sequences which make up the DigitalObject
	 */
	@Override
	public InputStream getObject();

	 /**
	 *
	 * Set the set of byte sequences.
	 *
	 * @param sd The Set of byte sequences which make up the DigitalObject.
	 *
	*/
	public void setObject(InputStream sd);

	/**
	 * Get the size in bytes of the DataObject
	 *
	 * @return the size in bytes  of the DigitalObject
	 */
	public BigInteger getSize();

	/**
	 * Set the size in bytes of the DataObject
	 *
	 * @param size the size in bytes  of the DigitalObject
	 */
	public void setSize(BigInteger size);
}