/**
 *
 */
package info.oais.infomodel.implementation;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

import info.oais.infomodel.interfaces.IdentifierObject;

//DG XXXX
//@JsonIgnoreType
@JsonPropertyOrder({"IdentifierString", "IdentifierType" } )
public class IdentifierObjectRefImpl implements IdentifierObject {
	@JsonIgnore
	String m_idString = null;
	@JsonIgnore
	String m_idType = null;
	/**
	 * Create new IdentifierObject
	 */
	public IdentifierObjectRefImpl() {
		super();
	}
	/**
	 * Create new IdentifierObject
	 *
	 *
	 * @param idType	Type of the identifier e.g. \"URI\"
	 * @param idStr	Character string of the IdentifierObject;
	 *
	 */
	public IdentifierObjectRefImpl(String idStr, String idType) {

		m_idType = idType;
		m_idString = idStr;
	}


	/**
	 * Get IdentifierObject name
	 *
	 * @return Character string of the IdentifierObject;
	 *
	 */
	@JsonGetter("IdentifierString")
	public String getIdentifierString() {
		return m_idString;  //(String)ident.get("IdName");
	}
	/**
	 * Get Type of the IdentifierObject
	 *
	 * @return Type of the IdentifierObject e.g. \"URI\"
	 *
	 */
	@JsonGetter("IdentifierType")
	public String getIdentifierType() {
		return m_idType; //(String)ident.get("IdType");
	}
	/**
	 * Set IdentifierObject name
	 *
	 * @param name String of the IdentifierObject
	 *
	 */
	@JsonSetter("IdentifierString")
	public void setIdentifierString(String idStr) {
		m_idString = idStr;
	}
	/**
	 * Set Type of IdentifierObject
	 *
	 * @param type Type of the IdentifierObject e.g. \"URI\";
	 *
	 */
	@JsonSetter("IdentifierType")
	public void setIdentifierType(String type) {
		m_idType = type;
	}

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
			return true;
		}
        if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
        if(this.getIdentifierString().equals(((IdentifierObjectRefImpl)obj).getIdentifierString()) && this.getIdentifierType().equals(((IdentifierObjectRefImpl)obj).getIdentifierType())) {
			return true;
		}
        return false;
    }

    @Override
    public int hashCode() {
        return getIdentifierString().hashCode()  + getIdentifierType().hashCode();
    }

    @Override
    public String toString() {
    	return "String = "+ m_idString + " Type = " + m_idType;
    }
}
