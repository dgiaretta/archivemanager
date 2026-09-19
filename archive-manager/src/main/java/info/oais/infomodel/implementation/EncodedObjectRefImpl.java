package info.oais.infomodel.implementation;

public class EncodedObjectRefImpl {
	
	String m_EncodedContent = null;
	String m_Encoding = null;
	String m_CustomEncoding = null;
	
	String getEncodedContent() {
		return m_EncodedContent;
	};

	void setEncodedContent(String encodedContent) {
		m_EncodedContent = encodedContent;
	};
	String getEncoding() {
		return m_Encoding;
	}

	void setEncoding(String encoding) {
		m_Encoding = encoding;
	}

	String getCustomEncoding() {
		return m_CustomEncoding;
	}

	void setCustomEncoding(String customEncoding) {
		m_CustomEncoding = customEncoding;
	}
	

}
