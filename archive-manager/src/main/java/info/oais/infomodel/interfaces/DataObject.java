package info.oais.infomodel.interfaces;

import java.math.BigInteger;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.DataObjectRefImpl;

/**
 * Either a Physical Object or a Digital Object. [OAIS]  Data: A reinterpretable
 * representation of information in a formalized manner suitable for communication,
 * interpretation, or processing. NOTE  - Examples of data include a sequence of
 * bits, a table of numbers, the characters on a page, the recording of sounds
 * made by a person speaking, or a moon rock specimen.  [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = DataObjectRefImpl.class)
public interface DataObject  {

	/**
	 * Get the Object (if any) which makes up the DataObject.
	 * The Object may be an instance of an
	 * <ul>
	 * <li>IdentifierObject, in which case it is a pointer to the
	 * actual DataObject</li>
	 * <li>String, in which case one may try to print it</li>
	 * <li>InputStream, in which case this is a Digital Object</li>
	 * <li>other specific types which we can deal with</li>
	 * </ul>
	 *
	 * @return The Object or null
	 */
	public Object getObject();

	/**
	 * Set the Object of the DataObject.
	 *
	 * @param obj The Object of the DataObject;
	 */
	public void setObject(Object obj);

	/**
	 * Get the IdentifierObject of the Information Object
	 *
	 * @return IdentifierObject for the Info Object
	 */
	public IdentifierObject getIdentifierObject();

	/**
	 * Set the IdentifierObject for the Info Object
	 *
	 * @param id IdentifierObject for the Info Object
	 */
	public void setIdentifierObject(IdentifierObject id);

	/**
	 * Get the IdentifierObject of the Data Object
	 *
	 * @return IdentifierObject for the Data Object
	 */
	public ObjVersion getObjVersion();

	/**
	 * Set the version for the Data Object
	 *
	 * @param v version for the Data Object
	 */
	public void setObjVersion(ObjVersion v);
	
	/**
	 * Get the size of the Data Object
	 *
	 * @return size of the Data Object
	 */
	public BigInteger getSize();
	
	/**
	 * Set the size of the Data Object
	 *
	 * @param size size of the Data Object
	 */
	public void setSize(BigInteger size);

}