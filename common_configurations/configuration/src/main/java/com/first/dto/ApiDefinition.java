package com.first.dto;

import java.util.List;

/**
 * Represents a single API definition loaded from a YAML config file.
 * Each YAML file under api-config/ maps to one or more of these objects.
 */
public class ApiDefinition {

    private String               id;
    private String               name;
    private String               version;
    private String               method;
    private String               path;
    private String               description;
    private boolean              enabled;
    private ApiSecurityConfig    security;       // renamed: was SecurityConfig (name clash)
    private RequestConfig        request;
    private ResponseConfig       response;
    private BusinessLogicConfig  businessLogic;
    private List<CallbackConfig> callbacks;

    public String getId()                         { return id; }
    public void   setId(String id)                { this.id = id; }

    public String getName()                       { return name; }
    public void   setName(String name)            { this.name = name; }

    public String getVersion()                    { return version; }
    public void   setVersion(String version)      { this.version = version; }

    public String getMethod()                     { return method; }
    public void   setMethod(String method)        { this.method = method; }

    public String getPath()                       { return path; }
    public void   setPath(String path)            { this.path = path; }

    public String getDescription()                { return description; }
    public void   setDescription(String d)        { this.description = d; }

    public boolean isEnabled()                    { return enabled; }
    public void    setEnabled(boolean enabled)    { this.enabled = enabled; }

    public ApiSecurityConfig getSecurity()                      { return security; }
    public void setSecurity(ApiSecurityConfig security)         { this.security = security; }

    public RequestConfig getRequest()                           { return request; }
    public void setRequest(RequestConfig request)               { this.request = request; }

    public ResponseConfig getResponse()                         { return response; }
    public void setResponse(ResponseConfig response)            { this.response = response; }

    public BusinessLogicConfig getBusinessLogic()               { return businessLogic; }
    public void setBusinessLogic(BusinessLogicConfig bl)        { this.businessLogic = bl; }

    public List<CallbackConfig> getCallbacks()                  { return callbacks; }
    public void setCallbacks(List<CallbackConfig> callbacks)    { this.callbacks = callbacks; }
}
