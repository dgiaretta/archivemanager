package info.oais.infomodel.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import info.oais.infomodel.interfaces.InformationPackage;
import info.oais.infomodel.implementation.InformationPackageRefImpl;

import java.io.IOException;

public class InformationPackageImplSerializer extends StdSerializer<InformationPackage> {
    public InformationPackageImplSerializer() {
        super(InformationPackage.class);
    }

    @Override
    public void serialize(InformationPackage value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        // Cast to implementation if needed
        if (value instanceof InformationPackageRefImpl) {
            InformationPackageRefImpl impl = (InformationPackageRefImpl) value;
            gen.writeStartObject();
            gen.writeObjectField("IdentifierObject", impl.getIdentifierObject());
            gen.writeObjectField("version", impl.getObjVersion());
            gen.writeObjectField("InformationObject", impl.getInformationObject());
            gen.writeObjectField("PDI", impl.getPDI());
            gen.writeObjectField("PackageDescription", impl.getPackageDescription());
            gen.writeObjectField("PackageType", impl.getPackageType());
            gen.writeObjectField("IsDeclaredComplete", impl.getIsDeclaredComplete());
            
            gen.writeEndObject();
        } else {
            // Fallback: default serialization
            provider.defaultSerializeValue(value, gen);
        }
    }
}