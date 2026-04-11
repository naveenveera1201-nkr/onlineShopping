package com.security;

import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.repository.NktDynamicRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    
    @Autowired
    private NktDynamicRepository repo;


    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String generateAccessToken(String userId, String userType, String jti) {
//        return Jwts.builder()
//                .subject(userId)
//                .claim("userType", userType)
//                .issuedAt(new Date())
//                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
//                .signWith(getSigningKey())
//                .compact();
        
        return Jwts.builder()
                .id(jti)
                .subject(userId)
                .claim("userType", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String userId, String jti) {
//        return Jwts.builder()
//                .subject(userId)
//                .claim("type", "refresh")
//                .issuedAt(new Date())
//                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
//                .signWith(getSigningKey())
//                .compact();
        
        return Jwts.builder()
                .id(jti)
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractUserType(String token) {
        return extractAllClaims(token).get("userType", String.class);
    }
    
    public String extractUId(String token) {
        return extractAllClaims(token).getId();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    public boolean isTokenValidWithDb(String token) {
        try {
            Claims claims = extractAllClaims(token);

            String jti = claims.getId();

            Map<String, Object> tokenDoc = repo.findOneByCriteria(
                    "auth_tokens",
                    Map.of("jti", jti, "isValid", true, "isLoggedOut", false)
            ).orElse(null);

            if (tokenDoc == null) return false;

            Date expiry = claims.getExpiration();
            if (expiry.before(new Date())) return false;

            return true;

        } catch (Exception e) {
            return false;
        }
    }
    
}
