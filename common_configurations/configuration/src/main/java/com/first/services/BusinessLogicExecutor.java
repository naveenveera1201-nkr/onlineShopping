package com.first.services;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.client.WorkFlowEngineClient;
import com.first.dto.ApiDefinition;
import com.first.dto.BusinessLogicConfig;
import com.first.functionalInterface.ProcessFlowInterface;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessLogicExecutor {

    private final DatabaseExecutor      databaseExecutor;
    private final ExternalApiExecutor   externalApiExecutor;
    private final CallbackExecutor      callbackExecutor;
    private final WorkFlowEngineClient  workFlowEngineClient;
    private final ObjectMapper          mapper;

    // ── Main dispatch ─────────────────────────────────────────────────────────

    public Map<String, Object> execute(ApiDefinition apiDef, Map<String, Object> params) {

        BusinessLogicConfig businessLogic = apiDef.getBusinessLogic();

        if (businessLogic == null) {
            return generateMockResponse(apiDef, params);
        }

        if ("Y".equalsIgnoreCase(businessLogic.getBusinessLogicRequired())) {
            String[] allowedFields = apiDef.getRequest() != null
                    ? apiDef.getRequest().getAllowedFields() : null;
            params = validateAllowedFields(params, allowedFields);
        }

        switch (businessLogic.getType().toUpperCase()) {
            case "DATABASE":
                return databaseExecutor.execute(businessLogic, params);
            case "EXTERNAL_API":
                return externalApiExecutor.execute(businessLogic, params);
            case "CUSTOM_SERVICE":
                return executeCustomService(businessLogic, params);
            case "MOCK":
            default:
                return generateMockResponse(apiDef, params);
        }
    }

    // ── Custom-service (calls Project 2 via Feign) ────────────────────────────

    private Map<String, Object> executeCustomService(BusinessLogicConfig config,
                                                     Map<String, Object> params) {
        try {
            String json   = mapper.writeValueAsString(params);
            ProcessFlowInterface call = workFlowEngineClient::process;
            String result = call.execute(json, config.getProcessCode());
            log.info("resposne :: {}", result);
            return mapper.readValue(result, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Custom service call failed for processCode={}: {}",
                      config.getProcessCode(), e.getMessage(), e);
//            throw new RuntimeException("Custom service execution failed: " + e.getMessage(), e);
            String msg = e.getMessage();

            if (msg != null && msg.contains("{\"status\"")) {
                int start = msg.indexOf("{\"status\"");
                int end = msg.lastIndexOf("}") + 1;
                msg = msg.substring(start, end);
            }

            throw new RuntimeException(msg);
            
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void executeCallbacks(ApiDefinition apiDef,
                                 Map<String, Object> response,
                                 String event) {
        if (apiDef.getCallbacks() == null) return;
        apiDef.getCallbacks().stream()
                .filter(cb -> cb.getEvent().equalsIgnoreCase(event))
                .forEach(cb -> callbackExecutor.execute(cb, response));
    }

    // ── Allowed-field filter ──────────────────────────────────────────────────

    public Map<String, Object> validateAllowedFields(Map<String, Object> requestBody,
                                                      String[] allowedFields) {
        if (allowedFields == null || allowedFields.length == 0) {
            return requestBody;
        }
        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));
        List<String> invalid = requestBody.keySet().stream()
                .filter(f -> !allowedSet.contains(f))
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Invalid fields present: " + invalid);
        }
        return requestBody;
    }

    // ── Mock response ─────────────────────────────────────────────────────────

    private Map<String, Object> generateMockResponse(ApiDefinition apiDef,
                                                      Map<String, Object> params) {
        Map<String, Object> response = new HashMap<>();
        if (apiDef.getResponse() != null && apiDef.getResponse().getFields() != null) {
            apiDef.getResponse().getFields().forEach(field -> {
                Object value = field.getDefaultValue() != null
                        ? field.getDefaultValue()
                        : generateMockValue(field.getType(), field.getName());
                response.put(field.getName(), value);
            });
        }
        return response;
    }

    private Object generateMockValue(String type, String fieldName) {
        if (type == null) return "mock-value";
        switch (type) {
            case "Long":          return System.currentTimeMillis();
            case "Integer":       return 100;
            case "Boolean":       return true;
            case "BigDecimal":    return 1000.50;
            case "LocalDateTime": return LocalDateTime.now().toString();
            case "LocalDate":     return LocalDateTime.now().toLocalDate().toString();
            case "String":
            default:
                return fieldName != null && fieldName.contains("Id")
                        ? UUID.randomUUID().toString()
                        : "mock-" + fieldName;
        }
    }
}
