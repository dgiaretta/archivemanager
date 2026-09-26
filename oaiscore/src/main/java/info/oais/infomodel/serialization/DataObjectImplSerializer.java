
package info.oais.infomodel.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.implementation.DataObjectRefImpl;
import info.oais.infomodel.implementation.EncodedObjectRefImpl;
import info.oais.infomodel.implementation.IdentifierObjectRefImpl;
import info.oais.infomodel.implementation.InformationObjectRefImpl;

import java.io.IOException;

public class DataObjectImplSerializer extends StdSerializer<DataObject> {
    public DataObjectImplSerializer() {
        super(DataObject.class);
    }

    @Override
    public void serialize(DataObject value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value instanceof DataObjectRefImpl) {
            DataObjectRefImpl impl = (DataObjectRefImpl) value;
            gen.writeStartObject();
            gen.writeObjectField("IdentifierObject", impl.getIdentifierObject());
            gen.writeObjectField("ObjVersion", impl.getObjVersion());
            Object obj = impl.getObject();
            
			if (obj instanceof EncodedObjectRefImpl ) {
				gen.writeObjectField("EncodedObject", (EncodedObjectRefImpl) obj);
			} 
			if (obj instanceof IdentifierObjectRefImpl ) {
				gen.writeObjectField("IdentifierObject", (InformationObjectRefImpl) obj);
			} 
			if (impl.getSize() != null) {
				gen.writeObjectField("size", impl.getSize());
			} 

            gen.writeEndObject();
        } else {
            provider.defaultSerializeValue(value, gen);
        }
    }
}
