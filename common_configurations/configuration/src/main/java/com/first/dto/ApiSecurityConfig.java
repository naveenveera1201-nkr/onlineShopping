package com.first.dto;

/**
 * Per-API security settings loaded from the YAML config.
 * Renamed from SecurityConfig to avoid collision with
 * the Spring Security @Configuration class of the same name.
 */
public class ApiSecurityConfig {

    private AuthenticationConfig authentication;
    private AuthorizationConfig  authorization;
    private RateLimitConfig      rateLimit;
    private EncryptionConfig     encryption;

    // ── Inner types ──────────────────────────────────────────────────────────

    public static class AuthenticationConfig {
        private boolean  enabled;
        private String   type;
        private String[] roles;
        private String   headerName;

        public boolean  isEnabled()    { return enabled; }
        public void     setEnabled(boolean enabled) { this.enabled = enabled; }
        public String   getType()      { return type; }
        public void     setType(String type) { this.type = type; }
        public String[] getRoles()     { return roles; }
        public void     setRoles(String[] roles) { this.roles = roles; }
        public String   getHeaderName(){ return headerName; }
        public void     setHeaderName(String headerName) { this.headerName = headerName; }
    }

    public static class AuthorizationConfig {
        private boolean enabled;
        private boolean resourceOwnerCheck;

        public boolean isEnabled()            { return enabled; }
        public void    setEnabled(boolean e)  { this.enabled = e; }
        public boolean isResourceOwnerCheck() { return resourceOwnerCheck; }
        public void    setResourceOwnerCheck(boolean v) { this.resourceOwnerCheck = v; }
    }

    public static class RateLimitConfig {
        private boolean enabled;
        private int     requestsPerMinute;
        private int     requestsPerHour;

        public boolean isEnabled()                  { return enabled; }
        public void    setEnabled(boolean e)        { this.enabled = e; }
        public int     getRequestsPerMinute()       { return requestsPerMinute; }
        public void    setRequestsPerMinute(int v)  { this.requestsPerMinute = v; }
        public int     getRequestsPerHour()         { return requestsPerHour; }
        public void    setRequestsPerHour(int v)    { this.requestsPerHour = v; }
    }

    public static class EncryptionConfig {
        private boolean enabled;
        private String  algorithm;

        public boolean isEnabled()               { return enabled; }
        public void    setEnabled(boolean e)     { this.enabled = e; }
        public String  getAlgorithm()            { return algorithm; }
        public void    setAlgorithm(String algo) { this.algorithm = algo; }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public AuthenticationConfig getAuthentication()                        { return authentication; }
    public void setAuthentication(AuthenticationConfig authentication)     { this.authentication = authentication; }
    public AuthorizationConfig  getAuthorization()                         { return authorization; }
    public void setAuthorization(AuthorizationConfig authorization)        { this.authorization = authorization; }
    public RateLimitConfig      getRateLimit()                             { return rateLimit; }
    public void setRateLimit(RateLimitConfig rateLimit)                   { this.rateLimit = rateLimit; }
    public EncryptionConfig     getEncryption()                            { return encryption; }
    public void setEncryption(EncryptionConfig encryption)                 { this.encryption = encryption; }
}
