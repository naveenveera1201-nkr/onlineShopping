package com.first.services;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.first.dto.ApiDefinition;
import com.first.dto.ApiSecurityConfig;
import com.first.dto.ApiSecurityConfig.AuthenticationConfig;
import com.first.exception.ApiSecurityException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-API security enforcement based on the YAML security config.
 *
 * JWT validation is handled upstream by JwtAuthenticationFilter which populates
 * the Spring SecurityContext. This service only performs:
 *   1. Role checking against the YAML-declared roles.
 *   2. In-memory rate limiting per api+client.
 *
 * JWT is intentionally NOT re-validated here to avoid duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {


    @Value("${jwt.secret}")
    private String jwtSecret;
    
//    private static final String JWT_SECRET = "your-secret-key-change-in-production";
    private Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public void validateSecurity(ApiDefinition apiDef,
                                 HttpServletRequest request, Map<String, String> headers) {
		
    	if (apiDef.getSecurity() == null)
			return;

		AuthenticationConfig auth = apiDef.getSecurity().getAuthentication();

		if (auth != null && auth.isEnabled()) {
			String token = extractToken(headers, auth);
			validateToken(token, auth);
		}
	}

    private String extractToken(Map<String, String> headers,
    		AuthenticationConfig auth) {
        String headerName = auth.getHeaderName() != null ?
                auth.getHeaderName() : "Authorization";

        String token = headers.get(headerName.toLowerCase());
        if (token == null || token.isEmpty()) {
            throw new SecurityException("Authentication token required");
        }

        return token;
    }

    private void validateToken(String token,
    		AuthenticationConfig auth) {
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

//            Claims claims = Jwts.parser()
//                    .setSigningKey(JWT_SECRET.getBytes())
//                    .parseClaimsJws(token)
//                    .getBody();
            
            Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

            String role = claims.get("userType", String.class);
           
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
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
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


    /**
     * Checks that the authenticated user has one of the roles declared in the
     * YAML security config. Skips the check for PUBLIC role.
     */
    public void validateRoles(ApiDefinition apiDef) {
        ApiSecurityConfig security = apiDef.getSecurity();
        if (security == null) return;

        ApiSecurityConfig.AuthenticationConfig auth = security.getAuthentication();
        if (auth == null || !auth.isEnabled()) return;

        String[] roles = auth.getRoles();
        if (roles == null || roles.length == 0
                || Arrays.asList(roles).contains("PUBLIC")) {
            return; // public endpoint — no role check needed
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiSecurityException("Authentication required");
        }

        boolean hasRole = authentication.getAuthorities().stream()
                .anyMatch(a -> Arrays.asList(roles).stream()
                        .anyMatch(r -> a.getAuthority().equalsIgnoreCase("ROLE_" + r)));

        if (!hasRole) {
            log.warn("Access denied for user={} to api={}, requiredRoles={}",
                    authentication.getPrincipal(), apiDef.getId(), Arrays.toString(roles));
            throw new ApiSecurityException("Insufficient permissions for this endpoint");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rate limiting (in-memory; replace with Bucket4j+Redis for production)
    // ─────────────────────────────────────────────────────────────────────────

    public void enforceRateLimit(ApiDefinition apiDef, HttpServletRequest request) {
        ApiSecurityConfig security = apiDef.getSecurity();
        if (security == null || security.getRateLimit() == null) return;

        ApiSecurityConfig.RateLimitConfig rl = security.getRateLimit();
        if (!rl.isEnabled()) return;

        String clientId = resolveClientId(request);
        String key      = apiDef.getId() + ":" + clientId;

        RateLimiter limiter = rateLimiters.computeIfAbsent(key,
                k -> new RateLimiter(rl.getRequestsPerMinute(), rl.getRequestsPerHour()));

        if (!limiter.allowRequest()) {
            log.warn("Rate limit exceeded: api={}, client={}", apiDef.getId(), clientId);
            throw new ApiSecurityException("Rate limit exceeded. Please try again later.");
        }
    }

    /** Prefers authenticated userId over IP address as the rate-limit key. */
    private String resolveClientId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getPrincipal().toString();
        }
        return request.getRemoteAddr();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: sliding-window rate limiter
    // ─────────────────────────────────────────────────────────────────────────

    private static final class RateLimiter {
        private final int maxPerMinute;
        private final int maxPerHour;
        private long      minuteWindowStart;
        private long      hourWindowStart;
        private int       minuteCount;
        private int       hourCount;

        RateLimiter(int maxPerMinute, int maxPerHour) {
            this.maxPerMinute    = maxPerMinute;
            this.maxPerHour      = maxPerHour;
            long now             = System.currentTimeMillis();
            this.minuteWindowStart = now;
            this.hourWindowStart   = now;
        }

        synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            if (now - minuteWindowStart > 60_000L)  { minuteWindowStart = now; minuteCount = 0; }
            if (now - hourWindowStart   > 3_600_000L){ hourWindowStart   = now; hourCount   = 0; }
            if (minuteCount >= maxPerMinute || hourCount >= maxPerHour) return false;
            minuteCount++;
            hourCount++;
            return true;
        }
    }
}
