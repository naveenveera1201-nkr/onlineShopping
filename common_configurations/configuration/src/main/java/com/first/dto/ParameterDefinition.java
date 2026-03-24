package com.first.dto;

public class ParameterDefinition {
    private String name;
    private String type;
    private String location;
    private boolean required;
    private boolean sensitive;
    private Object defaultValue;
    private ValidationConfig validation;

    public static class ValidationConfig {
        private Integer minLength;
        private Integer maxLength;
        private Integer min;
        private Integer max;
        private Integer scale;
        private String pattern;
        private String customRule;
        private String[] allowedValues;
        private String errorMessage;

        public Integer getMinLength() { return minLength; }
        public void setMinLength(Integer minLength) { this.minLength = minLength; }

        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

        public Integer getMin() { return min; }
        public void setMin(Integer min) { this.min = min; }

        public Integer getMax() { return max; }
        public void setMax(Integer max) { this.max = max; }

        public Integer getScale() { return scale; }
        public void setScale(Integer scale) { this.scale = scale; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public String getCustomRule() { return customRule; }
        public void setCustomRule(String customRule) { this.customRule = customRule; }

        public String[] getAllowedValues() { return allowedValues; }
        public void setAllowedValues(String[] allowedValues) { this.allowedValues = allowedValues; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }

    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

    public ValidationConfig getValidation() { return validation; }
    public void setValidation(ValidationConfig validation) { this.validation = validation; }
}