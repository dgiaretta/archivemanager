/**
 *
 */
package info.oais.infomodel.implementation;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.IdentifierObject;
import info.oais.infomodel.interfaces.ObjVersion;

/**
 * Reference implementation of DataObject.
 *
 * The DataObject has an IdentifierObject, so that it can be found.
 *
 * The DataObject content is an Object. This Object may be
 * hold the bytes or it may be an instance of an IdentifierObject,
 * in which can it may point to the location of the bytes,
 * or may point to a physical object.
 *
 * @author david
 *
 */
@JsonPropertyOrder({"EncodedObject", "IdentifierObject" } )
public class DataObjectRefImpl implements DataObject {
	/**
	 * Internal value of Data Object
	 */
	@JsonIgnore
	Object m_DATA = null;
	/**
	 * Internal value of the IdentifierObject
	 */
	@JsonIgnore
	IdentifierObject m_ID = null;
	/**
	 * Internal value of Version
	 */
	@JsonIgnore
	ObjVersion m_ObjVer = new ObjVersionRefImpl();
	
	@JsonIgnore
	BigInteger m_SIZE = null;

	/**
	 * Create new DataObject
	 *
	 */
	public DataObjectRefImpl() {
		super();
		/**
		 * Generate an initial UUID
		 */
		//m_ID = new IdentifierObjectRefImpl((UUID.randomUUID()).toString(), "UUID");
	}
	/**
	 * Create new DataObject
	 *
	 * @param obj Object for the DataObject
	 *
	 */
	public DataObjectRefImpl(Object obj) {

		m_DATA = obj;
		/**
		 * Generate an initial UUID
		 */
		//m_ID = new IdentifierObjectRefImpl((UUID.randomUUID()).toString(), "UUID");

	}


	/**
	 * The following methods are for JACKSON serialisation
	 * If the Object is an instance of IdentifierObject then we want the
	 * JSON to show an IdentifierObject rather than an Object
	 */
	/**
	 * The Data Object may in fact be an IdentifierObject which points to the actual Data Object, or an EncodedObject
	 * @return the EncodedObject OR else return an IdentifierObject
	 */
	@JsonGetter("Object")
	public Object getObject() {
		if (m_DATA instanceof IdentifierObject) {
			return (IdentifierObjectRefImpl)m_DATA;
		} else {
			return (EncodedObjectRefImpl)m_DATA;
		}
	}

	/**
	 * Set the Object of the DataObject.
	 *
	 * @param obj The Object of the DataObject;
	 */
	@JsonSetter("Object")
	public void setObject(Object obj) {
		m_DATA = obj;
	}

	/**
	 * Get the IdentifierObject of this DataObject.
	 *
	 * @return IdentifierObject of this DataObject.
	 */
	@JsonGetter("IdentifierObject")
	public IdentifierObject getIdentifierObject() {
		return m_ID;
	}
	/**
	 * Set the IdentifierObject of this DataObject.
	 *
	 * @param id IdentifierObject of this DataObject.
	 */
	@JsonSetter("IdentifierObject")
	public void setIdentifierObject(IdentifierObject id) {
		m_ID = id;
	}
	@Override
	@JsonIgnore
	public String toString() {
		return (String)m_DATA;
	}

	/**
	 * Return the version of the IO
	 *
	 * @return version
	 */
	@JsonGetter("ObjVersion")
	public ObjVersion getObjVersion() {

		return m_ObjVer;
	}

	/**
	 * Set the version for the IO
	 *
	 * @param ver The version to set
	 */
	@JsonSetter("ObjVersion")
	public void setObjVersion(ObjVersion ver) {
		m_ObjVer = ver;
	}
	@Override
	/**
	 * Get the size of the Data Object
	 *
	 * @return size of the Data Object
	 */
	public BigInteger getSize() {
		return m_SIZE;
	}
	@Override
	/**
	 * Set the size of the Data Object
	 *
	 * @param size size of the Data Object
	 */
	public void setSize(BigInteger size) {
		m_SIZE = size;
	}
}
