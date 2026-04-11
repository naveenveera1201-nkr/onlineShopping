package com.first.services;

import com.first.components.CustomValidationRules;
import com.first.dto.ApiDefinition;
import com.first.dto.ParameterDefinition;
import com.first.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates incoming request parameters against the rules defined in the
 * API YAML config (required, minLength, maxLength, pattern, allowedValues, etc.).
 * Returns a clean map of typed, validated parameter values.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final CustomValidationRules customRules;

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> validate(ApiDefinition apiDef,
                                        Map<String, Object> body,
                                        HttpServletRequest request,
                                        Map<String, String> headers) {
        Map<String, Object> validated = new HashMap<>();

        if (apiDef.getRequest() == null || apiDef.getRequest().getParameters() == null) {
            return validated;
        }

        for (ParameterDefinition param : apiDef.getRequest().getParameters()) {
            Object value = extractValue(param, body, request, headers);

            // Required check
            if (param.isRequired() && (value == null || value.toString().isBlank())) {
                throw new ValidationException(param.getName() + " is required");
            }

            // Apply default
            if (value == null && param.getDefaultValue() != null) {
                value = param.getDefaultValue();
            }

            // Validate and type-convert
            if (value != null && !value.toString().isBlank()) {
                validateParameter(param, value);
                value = convertType(value, param.getType());
                validated.put(param.getName(), value);
            }
        }

        log.debug("Validation passed for api={}, params={}", apiDef.getId(), validated.keySet());
        return validated;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private Object extractValue(ParameterDefinition param,
                                Map<String, Object> body,
                                HttpServletRequest request,
                                Map<String, String> headers) {
        return switch (param.getLocation().toUpperCase()) {
            case "BODY"   -> body != null ? body.get(param.getName()) : null;
            case "QUERY"  -> request.getParameter(param.getName());
            case "PATH"   -> extractPathVariable(param.getName(), request);
            case "HEADER" -> headers.get(param.getName().toLowerCase());
            default       -> null;
        };
    }

    /**
     * Extracts a path variable from the request attributes set by Spring MVC.
     * Falls back to the last URI segment for simple cases.
     */
    @SuppressWarnings("unchecked")
    private Object extractPathVariable(String varName, HttpServletRequest request) {
        Object uriVars = request.getAttribute(
                "org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
        if (uriVars instanceof Map) {
            Object val = ((Map<String, Object>) uriVars).get(varName);
            if (val != null) return val;
        }
        // Fallback: last segment of URI (simple id-only paths)
        String[] parts = request.getRequestURI().split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation rules
    // ─────────────────────────────────────────────────────────────────────────

    private void validateParameter(ParameterDefinition param, Object value) {
        ParameterDefinition.ValidationConfig v = param.getValidation();
        if (v == null) return;

        String str      = value.toString();
        String errorMsg = v.getErrorMessage() != null ? v.getErrorMessage()
                        : param.getName() + " validation failed";

        if (v.getMinLength() != null && str.length() < v.getMinLength())
            throw new ValidationException(errorMsg);

        if (v.getMaxLength() != null && str.length() > v.getMaxLength())
            throw new ValidationException(errorMsg);

        if (isNumericType(param.getType()))
            validateNumeric(param, value, v, errorMsg);

        if (v.getPattern() != null && !Pattern.matches(v.getPattern(), str))
            throw new ValidationException(errorMsg);

        if (v.getAllowedValues() != null
                && !Arrays.asList(v.getAllowedValues()).contains(str))
            throw new ValidationException(
                    errorMsg + ". Allowed: " + Arrays.toString(v.getAllowedValues()));

        if (v.getCustomRule() != null)
            customRules.validate(v.getCustomRule(), value, errorMsg);
    }

    private void validateNumeric(ParameterDefinition param, Object value,
                                 ParameterDefinition.ValidationConfig v, String errorMsg) {
        try {
            double num = Double.parseDouble(value.toString());
            if (v.getMin() != null && num < v.getMin())  throw new ValidationException(errorMsg);
            if (v.getMax() != null && num > v.getMax())  throw new ValidationException(errorMsg);
            if (v.getScale() != null && "BigDecimal".equals(param.getType())) {
                if (new BigDecimal(value.toString()).scale() > v.getScale())
                    throw new ValidationException("Max " + v.getScale() + " decimal places allowed");
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid numeric value for " + param.getName());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type conversion
    // ─────────────────────────────────────────────────────────────────────────

    private Object convertType(Object value, String type) {
        if (value == null) return null;
        try {
            return switch (type) {
                case "Integer"    -> Integer.parseInt(value.toString());
                case "Long"       -> Long.parseLong(value.toString());
                case "Double"     -> Double.parseDouble(value.toString());
                case "Float"      -> Float.parseFloat(value.toString());
                case "BigDecimal" -> new BigDecimal(value.toString());
                case "Boolean"    -> Boolean.parseBoolean(value.toString());
                case "LocalDate"  -> LocalDate.parse(value.toString());
                default           -> value.toString();
            };
        } catch (Exception e) {
            throw new ValidationException(
                    "Cannot convert '" + value + "' to " + type + ": " + e.getMessage());
        }
    }

    private boolean isNumericType(String type) {
        return "Integer".equals(type) || "Long".equals(type) || "Double".equals(type)
            || "BigDecimal".equals(type) || "Float".equals(type);
    }
}
