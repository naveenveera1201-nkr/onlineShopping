package com.configs.handlers;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.configs.ConfigType;
import com.configs.registries.process.ProcessRegistry;
import com.configs.validations.ConfigValidator;
import com.exceptions.ProcessFlowException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.Process;

public class ProcessConfigHandler implements ConfigHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessConfigHandler.class);

    private final ObjectMapper objectMapper;
    private final ConfigValidator<Process> validator;
    private final ProcessRegistry registry;

    public ProcessConfigHandler(ObjectMapper objectMapper,
                                ConfigValidator<Process> validator,
                                ProcessRegistry registry) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.registry = registry;
    }

    @Override
    public ConfigType type() {
        return ConfigType.PROCESS;
    }

    @Override
    public void load(InputStream input) throws Exception {

        LOGGER.info("Loading PROCESS configuration");

        long startTime = System.currentTimeMillis();

        Map<String, List<Process>> data;
        try {
            data = objectMapper.readValue(input, new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.error("Failed to parse PROCESS configuration JSON", e);
            throw new ProcessFlowException("failed to parse process configuration json");
        }

        List<Process> processes = data.get("Processes");
        if (processes == null || processes.isEmpty()) {
            LOGGER.warn("PROCESS configuration contains no processes");
            registry.clearAll();
            return;
        }

        registry.clearAll();
        int loadedCount = 0;

        for (Process process : processes) {

            try {
                validator.validate(process);
            } catch (ProcessFlowException e) {
                LOGGER.error(
                        "Invalid PROCESS definition [code={}]: {}",
                        process.getCode(),
                        e.getMessage()
                );
                throw e;
            }

            Process existingProcess = null;
            try {
                existingProcess = registry.get(process.getCode());
            } catch (ProcessFlowException ignored) {
            }
            if(existingProcess != null) {
                LOGGER.error("found duplicate process by code : {}", process.getCode());
                throw new ProcessFlowException("found duplicate process by code : " + process.getCode());
            }

            registry.add(process.getCode(), process);
            loadedCount++;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Loaded PROCESS [code={}, approvalFlow={}, levels={}]",
                        process.getCode(),
                        process.getApprovalFlow(),
                        process.getNoOfLevels()
                );
            }
        }

        LOGGER.info(
                "Loaded {} PROCESS definitions successfully in {} ms",
                loadedCount,
                System.currentTimeMillis() - startTime
        );
    }
}