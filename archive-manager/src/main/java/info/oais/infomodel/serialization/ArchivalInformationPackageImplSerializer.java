
package info.oais.infomodel.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import info.oais.infomodel.interfaces.ArchivalInformationPackage;
import info.oais.infomodel.implementation.ArchivalInformationPackageRefImpl;

import java.io.IOException;

public class ArchivalInformationPackageImplSerializer extends StdSerializer<ArchivalInformationPackage> {
    public ArchivalInformationPackageImplSerializer() {
        super(ArchivalInformationPackage.class);
    }

    @Override
    public void serialize(ArchivalInformationPackage value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value instanceof ArchivalInformationPackageRefImpl) {
            ArchivalInformationPackageRefImpl impl = (ArchivalInformationPackageRefImpl) value;
            gen.writeStartObject();
            // Serialize superclass fields
            gen.writeObjectField("IdentifierObject", impl.getIdentifierObject());
            gen.writeObjectField("version", impl.getObjVersion());
            gen.writeObjectField("InformationObject", impl.getInformationObject());
            gen.writeObjectField("PDI", impl.getPDI());
            gen.writeObjectField("PackageDescription", impl.getPackageDescription());
            gen.writeObjectField("PackageType", impl.getPackageType());
            gen.writeObjectField("IsDeclaredComplete", impl.getIsDeclaredComplete());

            gen.writeEndObject();
        } else {
            provider.defaultSerializeValue(value, gen);
        }
    }
}