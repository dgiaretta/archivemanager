
// src/main/java/info/oais/infomodel/serialization/InformationObjectImplSerializer.java
package info.oais.infomodel.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import info.oais.infomodel.interfaces.InformationObject;
import info.oais.infomodel.implementation.InformationObjectRefImpl;

import java.io.IOException;

public class InformationObjectImplSerializer extends StdSerializer<InformationObject> {
    public InformationObjectImplSerializer() {
        super(InformationObject.class);
    }

    @Override
    public void serialize(InformationObject value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        // Cast to implementation if needed
        if (value instanceof InformationObjectRefImpl) {
            InformationObjectRefImpl impl = (InformationObjectRefImpl) value;
            gen.writeStartObject();
            gen.writeObjectField("id", impl.getIdentifier());
            gen.writeObjectField("version", impl.getObjVersion());
            gen.writeObjectField("RepresentationInformation", impl.getRepresentationInformation());
            gen.writeObjectField("DataObject", impl.getDataObject());
            
            gen.writeEndObject();
        } else {
            // Fallback: default serialization
            provider.defaultSerializeValue(value, gen);
        }
    }
}