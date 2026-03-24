package com.configs.handlers;

import com.configs.ConfigType;
import com.configs.registries.status.StatusRegistry;
import com.configs.validations.ConfigValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class StatusConfigHandler implements ConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusConfigHandler.class);

    private final ObjectMapper objectMapper;
    private final ConfigValidator<Status> validator;
    private final StatusRegistry registry;

    public StatusConfigHandler(ObjectMapper objectMapper,
                               ConfigValidator<Status> validator,
                               StatusRegistry registry) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.registry = registry;
    }

    @Override
    public ConfigType type() {
        return ConfigType.STATUS;
    }

    @Override
    public void load(InputStream input) throws Exception {

        LOGGER.info("Loading STATUS configuration");

        Map<String, List<Status>> data;
        try {
            data = objectMapper.readValue(input, new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.error("Failed to parse STATUS configuration JSON", e);
            throw e;
        }

        List<Status> statuses = data.get("Statuses");
        if (statuses == null || statuses.isEmpty()) {
            LOGGER.warn("STATUS configuration contains no statuses");
            registry.clearAll();
            return;
        }

        registry.clearAll();
        int loadedCount = 0;

        for (Status status : statuses) {
            if (status.getCode() == null) {
                LOGGER.warn("Skipping STATUS entry with null code: {}", status);
                continue;
            }

            registry.add(status.getCode(), status);
            loadedCount++;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Loaded STATUS [code={}, description={}]",
                        status.getCode(),
                        status.getDescription()
                );
            }
        }

        LOGGER.info("Loaded {} STATUS definitions successfully", loadedCount);
    }
}