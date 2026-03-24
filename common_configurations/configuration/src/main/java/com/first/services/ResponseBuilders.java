package com.first.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.first.dto.ApiDefinition;
import com.first.dto.ResponseConfig;

@Service
public class ResponseBuilders {

    public Map<String, Object> build(ApiDefinition apiDef,
                                     Map<String, Object> result,
                                     Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        if (apiDef.getResponse() == null ||
                apiDef.getResponse().getFields() == null) {
            return result;
        }

        for (ResponseConfig.ResponseField field : apiDef.getResponse().getFields()) {
            // Check conditional
            if (field.getConditional() != null) {
                if (!evaluateCondition(field.getConditional(), request)) {
                    continue;
                }
            }
            
            LinkedHashMap<String,Object> actualResult =  (LinkedHashMap<String, Object>) result;
           
            Object value = actualResult.get(field.getName());
            
//            Object value = result.get(field.getName());
            if (value == null && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
            }

            response.put(field.getName(), value);
        }

        // Add standard fields
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("apiVersion", apiDef.getVersion());
		response.put("statusDescription",
				result.get("statusDesc") != null ? result.get("statusDesc") : apiDef.getDescription());
		response.put("statusCode", result.get("statusCode") != null ? result.get("statusCode") : "200");

        return response;
    }

    private boolean evaluateCondition(String condition, Map<String, Object> request) {
        // Simple condition evaluation
        // Format: #{request.field == value}
        if (condition.contains("==")) {
            String[] parts = condition.replace("#{", "").replace("}", "")
                    .split("==");
            if (parts.length == 2) {
                String fieldPath = parts[0].trim().replace("request.", "");
                String expectedValue = parts[1].trim();
                Object actualValue = request.get(fieldPath);
                return String.valueOf(actualValue).equals(expectedValue);
            }
        }
        return true;
    }
}