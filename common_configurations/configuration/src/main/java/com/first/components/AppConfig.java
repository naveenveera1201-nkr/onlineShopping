package com.first.components;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Application-wide infrastructure beans.
 * All components should inject ObjectMapper via constructor — never use "new ObjectMapper()".
 */
@Configuration
public class AppConfig {

    /**
     * Shared, thread-safe Jackson ObjectMapper.
     * - JavaTimeModule: serialises LocalDate / LocalDateTime correctly.
     * - WRITE_DATES_AS_TIMESTAMPS disabled: ISO-8601 strings instead of epoch numbers.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Shared RestTemplate for synchronous HTTP calls (webhooks, callbacks). */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
