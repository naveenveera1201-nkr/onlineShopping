package com.first.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.first.components.CustomValidationRules;
import com.first.dto.ApiDefinition;
import com.first.dto.ParameterDefinition;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ValidationService {

    @Autowired
    private CustomValidationRules customRules;

    public Map<String, Object> validate(ApiDefinition apiDef,
                                        Map<String, Object> body,
                                        HttpServletRequest request,
                                        Map<String, String> headers) {
        Map<String, Object> validatedParams = new HashMap<>();

        if (apiDef.getRequest() == null ||
                apiDef.getRequest().getParameters() == null) {
            return validatedParams;
        }

        for (ParameterDefinition param : apiDef.getRequest().getParameters()) {
            Object value = extractValue(param, body, request, headers);

            // Check required
            if (param.isRequired() && (value == null || value.toString().isEmpty())) {
                throw new ValidationException(param.getName() + " is required");
            }

            // Apply default value
            if (value == null && param.getDefaultValue() != null) {
                value = param.getDefaultValue();
            }

            // Validate if value exists
            if (value != null && !value.toString().isEmpty()) {
                validateParameter(param, value);
                value = convertType(value, param.getType());
                validatedParams.put(param.getName(), value);
            }
        }

        return validatedParams;
    }

    private Object extractValue(ParameterDefinition param,
                                Map<String, Object> body,
                                HttpServletRequest request,
                                Map<String, String> headers) {
        switch (param.getLocation().toUpperCase()) {
            case "BODY":
                return body != null ? body.get(param.getName()) : null;
            case "QUERY":
                return request.getParameter(param.getName());
            case "PATH":
                return extractPathVariable(param.getName(), request.getRequestURI());
            case "HEADER":
                return headers.get(param.getName().toLowerCase());
            default:
                return null;
        }
    }

    private String extractPathVariable(String varName, String uri) {
        // Extract path variable from URI
        // This is simplified - enhance based on your needs
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    private void validateParameter(ParameterDefinition param, Object value) {
        ParameterDefinition.ValidationConfig validation = param.getValidation();
        if (validation == null) return;

        String strValue = value.toString();
        String errorMsg = validation.getErrorMessage() != null ?
                validation.getErrorMessage() : param.getName() + " validation failed";

        // Length validation
        if (validation.getMinLength() != null &&
                strValue.length() < validation.getMinLength()) {
            throw new ValidationException(errorMsg);
        }

        if (validation.getMaxLength() != null &&
                strValue.length() > validation.getMaxLength()) {
            throw new ValidationException(errorMsg);
        }

        // Numeric validation
        if (isNumericType(param.getType())) {
            validateNumeric(param, value, validation, errorMsg);
        }

        // Pattern validation
        if (validation.getPattern() != null) {
            if (!Pattern.matches(validation.getPattern(), strValue)) {
                throw new ValidationException(errorMsg);
            }
        }

        // Allowed values
        if (validation.getAllowedValues() != null) {
            boolean valid = Arrays.asList(validation.getAllowedValues())
                    .contains(strValue);
            if (!valid) {
                throw new ValidationException(errorMsg + ". Allowed values: " +
                        Arrays.toString(validation.getAllowedValues()));
            }
        }

        // Custom rules
        if (validation.getCustomRule() != null) {
            customRules.validate(validation.getCustomRule(), value, errorMsg);
        }
    }

    private void validateNumeric(ParameterDefinition param, Object value,
                                 ParameterDefinition.ValidationConfig validation,
                                 String errorMsg) {
        try {
            double numValue = Double.parseDouble(value.toString());

            if (validation.getMin() != null && numValue < validation.getMin()) {
                throw new ValidationException(errorMsg);
            }

            if (validation.getMax() != null && numValue > validation.getMax()) {
                throw new ValidationException(errorMsg);
            }

            // Scale validation for BigDecimal
            if (validation.getScale() != null && param.getType().equals("BigDecimal")) {
                BigDecimal bd = new BigDecimal(value.toString());
                if (bd.scale() > validation.getScale()) {
                    throw new ValidationException(
                            "Maximum " + validation.getScale() + " decimal places allowed");
                }
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid numeric value for " + param.getName());
        }
    }

    private boolean isNumericType(String type) {
        return type.equals("Integer") || type.equals("Long") ||
                type.equals("Double") || type.equals("BigDecimal") ||
                type.equals("Float");
    }

    private Object convertType(Object value, String type) {
        if (value == null) return null;

        try {
            switch (type) {
                case "Integer":
                    return Integer.parseInt(value.toString());
                case "Long":
                    return Long.parseLong(value.toString());
                case "Double":
                    return Double.parseDouble(value.toString());
                case "Float":
                    return Float.parseFloat(value.toString());
                case "BigDecimal":
                    return new BigDecimal(value.toString());
                case "Boolean":
                    return Boolean.parseBoolean(value.toString());
                case "LocalDate":
                    return LocalDate.parse(value.toString());
                default:
                    return value.toString();
            }
        } catch (Exception e) {
            throw new ValidationException(
                    "Cannot convert value to " + type + ": " + e.getMessage());
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}