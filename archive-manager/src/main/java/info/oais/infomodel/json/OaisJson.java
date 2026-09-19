package info.oais.infomodel.json;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Usage:  
 * ObjectMapper mapper = OaisJson.createMapper();
 *
 * String json = mapper.writeValueAsString(oaisObject);
 * Object roundTripped = mapper.readValue(json, Object.class);
 */

public final class OaisJson {

    private OaisJson() {}

    public static ObjectMapper createMapper() {
        BasicPolymorphicTypeValidator ptv =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("info.oais.infomodel")
                        .build();

        ObjectMapper mapper = new ObjectMapper();

        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
                JsonTypeInfo.As.PROPERTY
        );

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        mapper.registerModule(OaisModule.create());
        return mapper;
    }
}
