package info.oais.infomodel.implementation.utility;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OLDJsonToJava {

    public  <T> T json2Java(String str, Class<T> classType){

    	T t = null;

    	 try {
    		ObjectMapper mapper = new ObjectMapper();
    	    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			t=mapper.readValue(str, classType);
		}catch (Exception e) {
			e.printStackTrace();
		}

        return t;
    }

}
