package com.configs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.nkt.NktProcessDefinition;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the {@code "NktProcesses"} array from {@code process-flow.json}
 * at application startup and builds an in-memory registry keyed by
 * process code.
 *
 * Every service that needs process metadata can inject this component and
 * call {@link #get(String)} to retrieve the definition.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NktProcessConfigLoader {

    private final ObjectMapper mapper;

    @Getter
    private final Map<String, NktProcessDefinition> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream is = new ClassPathResource("process-flow.json").getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode processes = root.get("NktProcesses");
            if (processes == null || !processes.isArray()) {
                log.warn("No 'NktProcesses' array found in process-flow.json");
                return;
            }
            for (JsonNode node : processes) {
                NktProcessDefinition def = mapper.treeToValue(node, NktProcessDefinition.class);
                if (def.getCode() != null) {
                    registry.put(def.getCode(), def);
                    log.debug("NKT process registered: {}", def.getCode());
                }
            }
            log.info("NktProcessConfigLoader: loaded {} process definitions", registry.size());
        } catch (Exception e) {
            log.error("Failed to load NKT process definitions from process-flow.json", e);
            throw new RuntimeException("NKT process config load failed", e);
        }
    }

    public NktProcessDefinition get(String code) {
        NktProcessDefinition def = registry.get(code);
        if (def == null)
            throw new RuntimeException("Unknown NKT process code: " + code);
        return def;
    }

    public boolean exists(String code) {
        return registry.containsKey(code);
    }
}
