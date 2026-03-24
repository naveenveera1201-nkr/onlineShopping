package com.first.dto;

import java.util.List;

public class ResponseConfig {
    private int successCode;
    private String contentType;
    private String[] blockedFields;
    
    public String[] getBlockedFields() {
		return blockedFields;
	}
	public void setBlockedFields(String[] blockedFields) {
		this.blockedFields = blockedFields;
	}
	private List<ResponseField> fields;

    public static class ResponseField {
        private String name;
        private String type;
        private String description;
        private Object defaultValue;
        private String conditional;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

        public String getConditional() { return conditional; }
        public void setConditional(String conditional) { this.conditional = conditional; }
    }

    public int getSuccessCode() { return successCode; }
    public void setSuccessCode(int successCode) { this.successCode = successCode; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public List<ResponseField> getFields() { return fields; }
    public void setFields(List<ResponseField> fields) { this.fields = fields; }
}