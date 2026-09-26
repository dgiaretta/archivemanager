package info.oais.infomodel.implementation.utility;

import com.fasterxml.jackson.databind.ObjectMapper;

/** This class is responsible for converting between JSON and Java Objects.
 * @author David Giaretta
 *
 */
public class Json2Java {

	/**
	 * Convert JSON to Java objects
	 *
	 * @param str The JSON string
	 * @param classType The class of the object to be created.
	 * @return the Java object.
	 */
    public  <T> T json2Java(String str, Class<T> classType){

    	T t = null;

    	 try {
    		ObjectMapper mapper = new ObjectMapper();
    	    // mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			t=mapper.readValue(str, classType);
		}catch (Exception e) {
			e.printStackTrace();
		}

        return t;
    }

}
