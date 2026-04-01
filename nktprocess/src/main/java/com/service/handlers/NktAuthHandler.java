package com.service.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import com.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Handles all 6 authentication operations.
 *  AUTH_SEND_OTP, AUTH_VERIFY_OTP, AUTH_REFRESH_TOKEN,
 *  AUTH_ENROL_BIOMETRIC, AUTH_VERIFY_BIOMETRIC, AUTH_LOGOUT
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NktAuthHandler {

    private final JwtTokenProvider jwt;

    private String str(Map<String, Object> d, String k) {
        Object v = d.get(k); return v == null ? null : v.toString();
    }

    private String json(ObjectMapper m, Object o) {
        try { return m.writeValueAsString(o); }
        catch (Exception e) { return "{\"error\":\"serialisation failed\"}"; }
    }

    /* ── AUTH_SEND_OTP ───────────────────────────────────────────────────── */
    public NktOperationHandler sendOtp() {
        return (data, userId, repo, mapper) -> {
            String identifier = str(data, "identifier");
            String purpose    = str(data, "purpose");

            if ("register".equals(purpose) &&
                    repo.exists("users", Map.of("identifier", identifier)))
                throw new RuntimeException("User already exists");

            String otp = String.format("%04d", new Random().nextInt(10000));
            repo.deleteAll("otpRecords", Map.of("identifier", identifier));

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("identifier",     identifier);
            rec.put("identifierType", str(data, "identifierType"));
            rec.put("otp",            otp);
            rec.put("userType",       str(data, "userType"));
            rec.put("purpose",        purpose);
            rec.put("used",           false);
            rec.put("attempts",       0);
            rec.put("createdAt",      LocalDateTime.now().toString());
            repo.insert("otpRecords", rec);

            log.info("OTP {} generated for {}", otp, identifier); // remove in prod
            return json(mapper, Map.of("message", "OTP sent successfully", "expiresInSeconds", 300));
        };
    }

    /* ── AUTH_VERIFY_OTP ────────────────────────────────────────────────── */
    public NktOperationHandler verifyOtp() {
        return (data, userId, repo, mapper) -> {
            String identifier = str(data, "identifier");
            String otp        = str(data, "otp");
            String purpose    = str(data, "purpose");

            Map<String, Object> rec = repo.findOneByCriteria("otpRecords",
                    Map.of("identifier", identifier, "used", false))
                    .orElseThrow(() -> new RuntimeException("OTP not found or expired"));

            if (!otp.equals(rec.get("otp")))
                throw new RuntimeException("Invalid OTP");

            repo.updateFirst("otpRecords",
                    Map.of("identifier", identifier, "used", false),
                    Map.of("used", true, "verifiedAt", LocalDateTime.now().toString()));

            Map<String, Object> user;
            if ("register".equals(purpose)) {
                user = new LinkedHashMap<>();
                user.put("identifier",     identifier);
                user.put("identifierType", rec.get("identifierType"));
                user.put("name",           str(data, "name"));
                user.put("email",          str(data, "email"));
                user.put("userType",       rec.get("userType"));
                user.put("status",         "ACTIVE");
                user.put("addresses",      new ArrayList<>());
                user.put("favouriteStoreIds", new ArrayList<>());
                user.put("createdAt",      LocalDateTime.now().toString());
                user.put("createdBy",      "SYSTEM");
                user = repo.insert("users", user);
            } else {
                user = repo.findOne("users", "identifier", identifier)
                        .orElseThrow(() -> new RuntimeException("User not found"));
            }

            String uid  = user.get("id").toString();
            String utyp = user.get("userType").toString();
            return json(mapper, Map.of(
                    "accessToken",  jwt.generateAccessToken(uid, utyp),
                    "refreshToken", jwt.generateRefreshToken(uid),
                    "userId",       uid,
                    "userType",     utyp));
        };
    }

    /* ── AUTH_REFRESH_TOKEN ─────────────────────────────────────────────── */
    public NktOperationHandler refreshToken() {
        return (data, userId, repo, mapper) -> {
            String rt = str(data, "refreshToken");
            if (!jwt.isTokenValid(rt)) throw new RuntimeException("Invalid refresh token");
            String uid = jwt.extractUserId(rt);
            Map<String, Object> user = repo.findById("users", uid)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return json(mapper, Map.of(
                    "accessToken", jwt.generateAccessToken(uid, user.get("userType").toString())));
        };
    }

    /* ── AUTH_ENROL_BIOMETRIC ───────────────────────────────────────────── */
    public NktOperationHandler enrolBiometric() {
        return (data, userId, repo, mapper) -> {
            Map<String, Object> tok = new LinkedHashMap<>();
            tok.put("userId",      userId);
            tok.put("deviceId",    str(data, "deviceId"));
            tok.put("tokenHash",   str(data, "biometricToken"));
            tok.put("platform",    str(data, "platform"));
            tok.put("status",      "ACTIVE");
            tok.put("createdAt",   LocalDateTime.now().toString());
            tok.put("updatedAt",   LocalDateTime.now().toString());
            repo.insert("biometricTokens", tok);
            return json(mapper, Map.of("message", "Biometric token enrolled successfully"));
        };
    }

    /* ── AUTH_VERIFY_BIOMETRIC ──────────────────────────────────────────── */
    public NktOperationHandler verifyBiometric() {
        return (data, userId, repo, mapper) -> {
            String deviceId = str(data, "deviceId");
            String hash     = str(data, "biometricToken");

            Map<String, Object> tok = repo.findOneByCriteria("biometricTokens",
                    Map.of("deviceId", deviceId, "status", "ACTIVE"))
                    .orElseThrow(() -> new RuntimeException("Device not enrolled"));

            if (!hash.equals(tok.get("tokenHash")))
                throw new RuntimeException("Biometric verification failed");

            String uid = tok.get("userId").toString();
            Map<String, Object> user = repo.findById("users", uid)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return json(mapper, Map.of(
                    "accessToken",  jwt.generateAccessToken(uid, user.get("userType").toString()),
                    "refreshToken", jwt.generateRefreshToken(uid)));
        };
    }

    /* ── AUTH_LOGOUT ────────────────────────────────────────────────────── */
    public NktOperationHandler logout() {
        return (data, userId, repo, mapper) ->
                json(mapper, Map.of("message", "Logged out successfully"));
    }
}
