package com.first.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.dto.ApiDefinition;
import com.first.dto.ResponseConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Shapes the raw service result into the response structure declared in the
 * API YAML config. Adds standard envelope fields (timestamp, apiVersion, statusCode).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResponseBuilders {

    private final ObjectMapper mapper;   // injected shared bean — never use new ObjectMapper()

    public Map<String, Object> build(ApiDefinition apiDef,
                                     Map<String, Object> result,
                                     Map<String, Object> request) {

        if (apiDef.getResponse() == null || apiDef.getResponse().getFields() == null) {
            return result;
        }

        // Safe conversion — fixes the previous (LinkedHashMap) unchecked cast
        Map<String, Object> resultMap = toMap(result);
        Map<String, Object> response  = new HashMap<>();

        for (ResponseConfig.ResponseField field : apiDef.getResponse().getFields()) {
            if (field.getConditional() != null
                    && !evaluateCondition(field.getConditional(), request)) {
                continue;
            }
            Object value = resultMap.get(field.getName());
            if (value == null && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
            }
            response.put(field.getName(), value);
        }

        // Standard envelope
        response.put("timestamp",         LocalDateTime.now().toString());
        response.put("apiVersion",        apiDef.getVersion());
        response.put("statusDescription", resultMap.getOrDefault("statusDesc", apiDef.getDescription()));
        response.put("statusCode",        resultMap.getOrDefault("statusCode", "200").toString());

        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Thread-safe, type-safe conversion via ObjectMapper — no unchecked casts. */
    private Map<String, Object> toMap(Object obj) {
        try {
            return mapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Could not convert result to Map, returning as-is: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Evaluates a simple condition string of the form {@code #{request.field == value}}.
     */
    private boolean evaluateCondition(String condition, Map<String, Object> request) {
        if (condition == null || !condition.contains("==")) return true;
        try {
            String expr  = condition.replace("#{", "").replace("}", "").trim();
            String[] parts = expr.split("==");
            if (parts.length != 2) return true;
            String field    = parts[0].trim().replace("request.", "");
            String expected = parts[1].trim().replace("'", "").replace("\"", "");
            Object actual   = request.get(field);
            return String.valueOf(actual).equals(expected);
        } catch (Exception e) {
            log.warn("Condition evaluation failed: {}", e.getMessage());
            return true;
        }
    }
}
