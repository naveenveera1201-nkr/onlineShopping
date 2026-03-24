package com.first.dto;

import java.util.List;

public class RequestConfig {
    private String contentType;
    private String[] allowedFields;
   
    public String[] getAllowedFields() {
		return allowedFields;
	}
	public void setAllowedFields(String[] allowedFields) {
		this.allowedFields = allowedFields;
	}
	private List<ParameterDefinition> parameters;

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public List<ParameterDefinition> getParameters() { return parameters; }
    public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }
}