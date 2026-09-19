
// src/main/java/info/oais/infomodel/serialization/OaisJacksonModule.java
package info.oais.infomodel.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import info.oais.infomodel.interfaces.*;
import info.oais.infomodel.implementation.*;

public class OaisJacksonModule extends SimpleModule {
    public OaisJacksonModule() {
        addSerializer(InformationObject.class, new InformationObjectImplSerializer());
        addSerializer(InformationPackage.class, new InformationPackageImplSerializer());
        addSerializer(ArchivalInformationPackage.class, new ArchivalInformationPackageImplSerializer());
        // Register more serializers as needed
    }
}