package com.first.dto;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "api_logs")
public class ApiRequestResponseLog {

    @Id
    private String id;

    private String apiId;       // e.g. user-registration
    private String path;        // request URI
    private String method;      // GET/POST etc.

    private Map<String, String> requestHeaders;
    private String requestBody;

    private int responseStatus;
    private Map<String, String> responseHeaders;
    private String responseBody;

//    private OffsetDateTime timestamp;

//    public ApiRequestResponseLog() {
//        this.timestamp = OffsetDateTime.now();
//    }

    // Getters & setters (or use Lombok @Data)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApiId() { return apiId; }
    public void setApiId(String apiId) { this.apiId = apiId; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Map<String, String> getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(Map<String, String> requestHeaders) { this.requestHeaders = requestHeaders; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public int getResponseStatus() { return responseStatus; }
    public void setResponseStatus(int responseStatus) { this.responseStatus = responseStatus; }

    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, String> responseHeaders) { this.responseHeaders = responseHeaders; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

//    public OffsetDateTime getTimestamp() { return timestamp; }
//    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}