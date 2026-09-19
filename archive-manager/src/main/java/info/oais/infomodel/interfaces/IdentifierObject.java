package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.IdentifierObjectRefImpl;

/**
 * An identifier is a name that identifies (that is, labels the identity of)
 * either a unique object or a unique class of objects, where the "object" or
 * class may be an idea, physical countable object (or class thereof), or physical
 * noncountable substance (or class thereof). [OAIS]
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
@JsonDeserialize(as = IdentifierObjectRefImpl.class)
public interface IdentifierObject {

	/**
	 * Gets the String of the identifier e.g. http://pds.gov:8080
	 *
	 * @return 	String of the identifier
	 */
	public String	getIdentifierString();
	/**
	 * Gets the String of the type of the identifier e.g. \"URI\"
	 *
	 * @return 	String of the identifier
	 */
	public String	getIdentifierType();
	/**
	 * Sets the String of the identifier e.g. http://pds.gov:8080
	 *
	 * @param name 	String of the identifier
	 */
	public void	setIdentifierString(String name);
	/**
	 * Sets the String of the type of the identifier e.g. \"URI\"
	 * sets the String which identifies the identifier type
	 * e.g. URN, URI, local etc
	 *
	 * @param type 	String of the identifier
	 */
	public void	setIdentifierType(String type);


}