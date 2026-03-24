package com.first.services;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.first.dto.ApiDefinition;
import com.first.dto.SecurityConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class SecurityService {

    private static final String JWT_SECRET = "your-secret-key-change-in-production";
    private Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public void validateSecurity(ApiDefinition apiDef,
                                 HttpServletRequest request,
                                 Map<String, String> headers) {
        if (apiDef.getSecurity() == null) return;

        SecurityConfig.AuthenticationConfig auth =
                apiDef.getSecurity().getAuthentication();

        if (auth != null && auth.isEnabled()) {
            String token = extractToken(headers, auth);
            validateToken(token, auth);
        }
    }

    private String extractToken(Map<String, String> headers,
                                SecurityConfig.AuthenticationConfig auth) {
        String headerName = auth.getHeaderName() != null ?
                auth.getHeaderName() : "Authorization";

        String token = headers.get(headerName.toLowerCase());
        if (token == null || token.isEmpty()) {
            throw new SecurityException("Authentication token required");
        }

        return token;
    }

    private void validateToken(String token,
                               SecurityConfig.AuthenticationConfig auth) {
        switch (auth.getType().toUpperCase()) {
            case "JWT":
                validateJWT(token, auth.getRoles());
                break;
            case "API_KEY":
                validateApiKey(token);
                break;
            case "BASIC":
                validateBasicAuth(token);
                break;
            case "OAUTH2":
                validateOAuth2(token);
                break;
        }
    }

    private void validateJWT(String token, String[] allowedRoles) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Claims claims = Jwts.parser()
                    .setSigningKey(JWT_SECRET.getBytes())
                    .parseClaimsJws(token)
                    .getBody();

            String role = claims.get("role", String.class);
            if (allowedRoles != null && allowedRoles.length > 0) {
                boolean hasRole = Arrays.asList(allowedRoles).contains(role);
                if (!hasRole) {
                    throw new SecurityException("Insufficient permissions");
                }
            }
        } catch (Exception e) {
            throw new SecurityException("Invalid or expired token: " + e.getMessage());
        }
    }

    private void validateApiKey(String apiKey) {
        // Implement API key validation logic
        if (apiKey == null || apiKey.length() < 32) {
            throw new SecurityException("Invalid API key");
        }
    }

    private void validateBasicAuth(String authHeader) {
        if (!authHeader.startsWith("Basic ")) {
            throw new SecurityException("Invalid Basic Auth format");
        }
    }

    private void validateOAuth2(String token) {
        // Implement OAuth2 validation
        if (token == null || token.isEmpty()) {
            throw new SecurityException("Invalid OAuth2 token");
        }
    }

    public void enforceRateLimit(ApiDefinition apiDef, HttpServletRequest request) {
        SecurityConfig.RateLimitConfig rateLimit =
                apiDef.getSecurity().getRateLimit();

        if (rateLimit == null || !rateLimit.isEnabled()) return;

        String clientId = getClientIdentifier(request);
        String key = apiDef.getId() + ":" + clientId;

        RateLimiter limiter = rateLimiters.computeIfAbsent(key,
                k -> new RateLimiter(rateLimit.getRequestsPerMinute(),
                        rateLimit.getRequestsPerHour()));

        if (!limiter.allowRequest()) {
            throw new SecurityException("Rate limit exceeded");
        }
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Try to get user ID from token, otherwise use IP
        return request.getRemoteAddr();
    }

    private static class RateLimiter {
        private final int maxPerMinute;
        private final int maxPerHour;
        private long minuteWindow;
        private long hourWindow;
        private int minuteCount;
        private int hourCount;

        public RateLimiter(int maxPerMinute, int maxPerHour) {
            this.maxPerMinute = maxPerMinute;
            this.maxPerHour = maxPerHour;
            this.minuteWindow = System.currentTimeMillis();
            this.hourWindow = System.currentTimeMillis();
        }

        public synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();

            // Check minute window
            if (now - minuteWindow > 60000) {
                minuteWindow = now;
                minuteCount = 0;
            }

            // Check hour window
            if (now - hourWindow > 3600000) {
                hourWindow = now;
                hourCount = 0;
            }

            if (minuteCount >= maxPerMinute || hourCount >= maxPerHour) {
                return false;
            }

            minuteCount++;
            hourCount++;
            return true;
        }
    }
}