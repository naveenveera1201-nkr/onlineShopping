package com.first.dto;

import java.util.List;

public class ApiDefinition {
    private String id;
    private String name;
    private String version;
    private String method;
    private String path;
    private String description;
    private boolean enabled;
    private SecurityConfig security;
    private RequestConfig request;
    private ResponseConfig response;
    private BusinessLogicConfig businessLogic;
    private List<CallbackConfig> callbacks;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }

    public RequestConfig getRequest() { return request; }
    public void setRequest(RequestConfig request) { this.request = request; }

    public ResponseConfig getResponse() { return response; }
    public void setResponse(ResponseConfig response) { this.response = response; }

    public BusinessLogicConfig getBusinessLogic() { return businessLogic; }
    public void setBusinessLogic(BusinessLogicConfig businessLogic) {
        this.businessLogic = businessLogic;
    }

    public List<CallbackConfig> getCallbacks() { return callbacks; }
    public void setCallbacks(List<CallbackConfig> callbacks) { this.callbacks = callbacks; }
}
