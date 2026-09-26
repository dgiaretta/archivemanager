package info.oais.infomodel.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import info.oais.infomodel.implementation.EncodedObjectRefImpl;

/**
 * The EncodedData Object is an encoded binary object.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
@JsonDeserialize(as = EncodedObjectRefImpl.class)
public interface EncodedObject  {

	
	String getEncodedContent();
	void setEncodedContent(String encodedContent);
	String getEncoding();
	void setEncoding(String encoding);
	String getCustomEncoding();
	void setCustomEncoding(String customEncoding);
	
}