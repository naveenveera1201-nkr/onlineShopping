package com.first.dto;

import java.util.List;
import java.util.Map;

public class BusinessLogicConfig {
	private String businessLogicRequired;
	private String processCode;
    private String type;
    private String dataSource;
    private String endpoint;
    private String method;
    private int timeout;
    private List<OperationConfig> operations;
    private RetryPolicy retryPolicy;

    public static class OperationConfig {
        private String type;
        private String entity;
        private String where;
        private Map<String, String> mapping;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getEntity() { return entity; }
        public void setEntity(String entity) { this.entity = entity; }

        public String getWhere() { return where; }
        public void setWhere(String where) { this.where = where; }

        public Map<String, String> getMapping() { return mapping; }
        public void setMapping(Map<String, String> mapping) { this.mapping = mapping; }
    }

    public static class RetryPolicy {
        private boolean enabled;
        private int maxAttempts;
        private int backoffMultiplier;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public int getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(int backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

	public String getBusinessLogicRequired() {
		return businessLogicRequired;
	}

	public void setBusinessLogicRequired(String businessLogicRequired) {
		this.businessLogicRequired = businessLogicRequired;
	}
	
	public String getProcessCode() {
		return processCode;
	}

	public void setProcessCode(String processCode) {
		this.processCode = processCode;
	}
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public List<OperationConfig> getOperations() { return operations; }
    public void setOperations(List<OperationConfig> operations) { this.operations = operations; }

    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }
}