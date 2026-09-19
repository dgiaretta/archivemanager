
// src/main/java/info/oais/infomodel/config/JacksonConfig.java
package info.oais.infomodel.config;

import com.fasterxml.jackson.databind.Module;
import info.oais.infomodel.serialization.OaisJacksonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Module oaisJacksonModule() {
        return new OaisJacksonModule();
    }
}