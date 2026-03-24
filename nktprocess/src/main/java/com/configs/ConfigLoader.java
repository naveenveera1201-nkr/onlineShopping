package com.configs;

import com.configs.handlers.ConfigHandler;
import com.configs.handlers.ProcessConfigHandler;
import com.configs.handlers.StatusConfigHandler;
import com.configs.registries.process.ProcessRegistry;
import com.configs.registries.status.StatusRegistry;
import com.configs.validations.ProcessConfigValidator;
import com.configs.validations.StatusConfigValidator;
import com.exceptions.ProcessFlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    private final Map<ConfigType, ConfigHandler> handlers;

    ConfigLoader(List<ConfigHandler> handlerList) {
        this.handlers = handlerList.stream().collect(Collectors.toMap(ConfigHandler::type, Function.identity()));
    }

    public static ConfigLoader defaultConfigLoader(ObjectMapper objectMapper, ProcessRegistry processRegistory, StatusRegistry statusRegistory) {
        return new ConfigLoader(List.of(
                new ProcessConfigHandler(objectMapper, new ProcessConfigValidator(), processRegistory),
                new StatusConfigHandler(objectMapper, new StatusConfigValidator(), statusRegistory)
        ));
    }

    public void loadAll(Map<ConfigType, String> externalPaths) throws ProcessFlowException {

        for (ConfigType type : handlers.keySet()) {
            loadSingle(type, externalPaths.get(type));
        }
    }

    private void loadSingle(ConfigType type, String externalPath) throws ProcessFlowException {

        try (InputStream is = resolve(type, externalPath)) {
            handlers.get(type).load(is);
            logger.info("Loaded config: {}", type);
        } catch (Exception e) {
            throw new ProcessFlowException("Failed loading config: " + type, e);
        }
    }

    private InputStream resolve(ConfigType type, String externalPath) throws IOException {
        if (externalPath != null && !externalPath.trim().isEmpty()) {
            Path extPath = Paths.get(externalPath).normalize();
            if (Files.exists(extPath)) {
                return Files.newInputStream(extPath);
            }
        }

        InputStream is = getClass().getClassLoader().getResourceAsStream(type.defaultFile());

        if (is == null) {
            throw new FileNotFoundException("Missing config: " + type);
        }
        return is;
    }
}