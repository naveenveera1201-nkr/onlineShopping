package com.first.components;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.first.dto.ApiDefinition;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads all YAML files under classpath:api-config/ at startup and builds
 * an in-memory registry keyed by "METHOD:path".
 * Regex patterns for path-variable matching are pre-compiled and cached.
 */
@Component
@Slf4j
public class ApiConfigLoader {

    // Registry: "GET:/api/v1/users/{id}" → ApiDefinition
    private final Map<String, ApiDefinition> apiRegistry     = new ConcurrentHashMap<>();
    // Pre-compiled path-pattern cache: "/api/v1/users/{id}" → Pattern
    private final Map<String, Pattern>       patternCache    = new ConcurrentHashMap<>();

    private final ObjectMapper               yamlMapper      =
            new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    // ── Startup load ──────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void loadAllConfigs() {
        apiRegistry.clear();
        patternCache.clear();
        try {
            Resource[] resources = resolver.getResources("classpath*:api-config/*.yml");
            log.info("Found {} API config file(s) in classpath:api-config/", resources.length);
            for (Resource r : resources) {
                loadFile(r);
            }
            log.info("Total APIs loaded: {}", apiRegistry.size());
        } catch (Exception e) {
            log.error("Failed to scan api-config folder: {}", e.getMessage(), e);
        }
    }

    private void loadFile(Resource r) {
        log.info(" -> loading: {}", r.getFilename());
        try (InputStream is = r.getInputStream()) {
            JsonNode root     = yamlMapper.readTree(is);
            JsonNode apisNode = root.get("apis");
            if (apisNode == null || !apisNode.isArray()) {
                log.warn("    -> no top-level 'apis' array in {}", r.getFilename());
                return;
            }
            List<ApiDefinition> list = yamlMapper.convertValue(
                    apisNode, new TypeReference<List<ApiDefinition>>() {});
            for (ApiDefinition api : list) {
                String key = api.getMethod() + ":" + api.getPath();
                apiRegistry.put(key, api);
                // pre-compile path pattern
                patternCache.put(api.getPath(), buildPattern(api.getPath()));
            }
            log.info("    -> {} API(s) registered from {}", list.size(), r.getFilename());
        } catch (Exception ex) {
            log.error("    -> failed to parse {}: {}", r.getFilename(), ex.getMessage(), ex);
        }
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Finds the ApiDefinition that matches a given HTTP method + URI.
     * First tries direct key lookup; then falls back to path-pattern matching.
     */
    public ApiDefinition findApi(String method, String path) {
        // 1. Exact match
        ApiDefinition exact = apiRegistry.get(method + ":" + path);
        if (exact != null) return exact;

        // 2. Path-variable pattern match
        for (Map.Entry<String, ApiDefinition> entry : apiRegistry.entrySet()) {
            ApiDefinition def = entry.getValue();
            if (!def.getMethod().equalsIgnoreCase(method)) continue;
            Pattern p = patternCache.computeIfAbsent(def.getPath(), this::buildPattern);
            if (p.matcher(path).matches()) return def;
        }
        return null;
    }

    public Map<String, ApiDefinition> getAllApis() {
        return apiRegistry;
    }

    public void reloadConfiguration() {
        loadAllConfigs();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Pattern buildPattern(String pathTemplate) {
        // Replace {variableName} with a regex segment that matches any non-slash value
        String regex = pathTemplate.replaceAll("\\{[^}]+\\}", "[^/]+");
        return Pattern.compile(regex);
    }
}
