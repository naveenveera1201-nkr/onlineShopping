package com.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Application-level bean configuration.
 *
 * Provides a shared {@link ObjectMapper} with Java 8 date/time support so
 * every NKT service can inject it via constructor and serialize responses
 * consistently (ISO-8601 dates, no timestamps).
 */
@Configuration
public class AppConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Enables multi-document transactions against MongoDB.
     *
     * Added for the Excel stock-master import ({@code EXCEL_STOCK_MASTER_IMPORT}),
     * which must write categories + sub_categories + stocks as a single
     * all-or-nothing unit. No transaction manager existed in this project
     * before — nothing else currently uses {@code @Transactional} against
     * Mongo — so this bean was missing, not disabled. It works here because
     * the configured connection (`nkt-cluster.*.mongodb.net`, a `mongodb+srv://`
     * Atlas URI) is a replica set; Mongo only supports transactions against a
     * replica set or sharded cluster, never a lone standalone instance. Spring
     * Boot auto-detects this single {@link MongoTransactionManager} bean and
     * wires {@code @Transactional} support for it automatically — no further
     * configuration needed.
     */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
