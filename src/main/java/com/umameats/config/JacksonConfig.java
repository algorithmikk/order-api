package com.umameats.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4 no longer auto-exposes a com.fasterxml ObjectMapper bean when only
 * jackson-databind is on the classpath.
 *
 * <p>This mapper serves the messaging layer, so it needs java.time support:
 * order events carry the {@code Order} entity with its {@code LocalDateTime}
 * orderDate, and a bare mapper rejects those outright.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
