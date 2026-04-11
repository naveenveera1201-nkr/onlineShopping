package com.service.handlers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repository.NktDynamicRepository;
import com.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles all 6 authentication operations.
 *  AUTH_SEND_OTP, AUTH_VERIFY_OTP, AUTH_REFRESH_TOKEN,
 *  AUTH_ENROL_BIOMETRIC, AUTH_VERIFY_BIOMETRIC, AUTH_LOGOUT
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NktAuthHandler {
	
    @Value("${otp.expiration}")
    private String otpExpiration;

    private final JwtTokenProvider jwt;

	private String str(Map<String, Object> d, String k) {
		Object v = d.get(k);
		return v == null ? null : v.toString();
	}

	private String json(ObjectMapper m, Object o) {
		try {
			return m.writeValueAsString(o);
		} catch (Exception e) {
			return "{\"error\":\"serialisation failed\"}";
		}
	}

	/* ── AUTH_SEND_OTP ───────────────────────────────────────────────────── */
	public NktOperationHandler sendOtp() {
		
		return (data, userId, repo, mapper, def) -> {
			
			String identifier = str(data, "identifier");
			String purpose = str(data, "purpose");
			String userType = str(data, "userType");

			if ("register".equals(purpose)) {
				if (userType.equalsIgnoreCase("business")
						&& repo.exists("businessusers", Map.of("identifier", identifier))) {
//					throw new RuntimeException("Business user already exists");
					return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "statusDesc", "Business user already exists"
	                ));
				}

				if (userType.equalsIgnoreCase("customer")
						&& repo.exists("customerusers", Map.of("identifier", identifier))) {
//					throw new RuntimeException("Customer user already exists");
					return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "statusDesc", "customer user already exists"
	                ));
					
				}

				if (userType.equalsIgnoreCase("admin")
						&& repo.exists("adminusers", Map.of("identifier", identifier))) {
//					throw new RuntimeException("Admin user already exists");
					return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "statusDesc", "Admin user already exists"
	                ));
				}
			}
			String otp = String.format("%04d", new Random().nextInt(10000));
			repo.deleteAll("otp_records", Map.of("identifier", identifier));

			Map<String, Object> rec = new LinkedHashMap<>();
			rec.put("identifier", identifier);
			rec.put("identifierType", str(data, "identifierType"));
			rec.put("otp", otp);
			rec.put("userType", str(data, "userType"));
			rec.put("purpose", purpose);
			rec.put("used", false);
			rec.put("attempts", 0);
			rec.put("expiryTime", LocalDateTime.now().plusMinutes(otpExpiration != null ? Long.parseLong(otpExpiration) : 3).toString());
			rec.put("createdAt", LocalDateTime.now().toString());
			repo.insert("otp_records", rec);

			log.info("OTP {} generated for {}", otp, identifier); // remove in prod
			return json(mapper, Map.of("message", "OTP sent successfully", "expiresInSeconds", 300));
		};
	}

    /* ── AUTH_VERIFY_OTP ────────────────────────────────────────────────── */
    public NktOperationHandler verifyOtp() {
        return (data, userId, repo, mapper, def) -> {
            String identifier = str(data, "identifier");
            String otp        = str(data, "otp");
            String purpose    = str(data, "purpose");
            String userType   =  str(data, "userType");

			Map<String, Object> rec = repo.findOneByCriteria("otp_records",
					Map.of("identifier", identifier, "used", false, "userType", userType)).orElse(null);

            if (rec == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "OTP not found or expired"
                ));
            }

			LocalDateTime createdAt = LocalDateTime.parse(rec.get("createdAt").toString());

			LocalDateTime expiryTime = createdAt.plusMinutes(otpExpiration != null ? Long.parseLong(otpExpiration) : 3);

			if (LocalDateTime.now().isAfter(expiryTime)) {

				// mark as used/expired (optional but recommended)
				repo.updateFirst("otp_records", Map.of("identifier", identifier, "used", false), Map.of("used", true));

				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "OTP expired"));
			}
            
			if (!otp.equals(rec.get("otp"))) {
				
				int attempts = (int) rec.get("attempts") + 1;
				
				repo.updateFirst("otp_records", Map.of("identifier", identifier, "used", false),
						Map.of("attempts", attempts));
				
//				return json(mapper, Map.of("status", "Failed", "message", "Invalid OTP"));
//				throw new RuntimeException("Invalid OTP");
				return json(mapper,Map.of(
            			"messsage",  "Invalid OTP",
            			"status", "Failed"));
			}

            repo.updateFirst("otp_records",
                    Map.of("identifier", identifier, "used", false),
                    Map.of("used", true, "verifiedAt", LocalDateTime.now().toString()));
            
            Map<String, Object> user;
            
            String tableName = rec.get("userType") + def.getCollection();
           
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

				user = repo.insert(tableName, user);
            } else {
                user = repo.findOne(tableName, "identifier", identifier)
                        .orElseThrow(() -> new RuntimeException("User not found"));
            }

            String uid  = user.get("id").toString();
            String utyp = user.get("userType").toString();
            String jti = java.util.UUID.randomUUID().toString();

            String accessToken  = jwt.generateAccessToken(uid, utyp, jti);
            String refreshToken = jwt.generateRefreshToken(uid, jti);
            
            insertToken(repo, uid, utyp, jti, accessToken, refreshToken);
           
            return json(mapper, Map.of("data", Map.of(
            		"accessToken", accessToken, 
            		"refreshToken", refreshToken,
					"userId", uid,
					"userType", utyp,
					"statusCode", "N200",
					"statusDesc", "Success")));
	        
        };
    }

	public void insertToken(NktDynamicRepository repo, String uid, String utyp, String jti, String accessToken,
			String refreshToken) {
		Map<String, Object> tokenDoc = new LinkedHashMap<>();
		tokenDoc.put("userId", uid);
		tokenDoc.put("userType", utyp);
		tokenDoc.put("accessToken", accessToken);
		tokenDoc.put("refreshToken", refreshToken);
		tokenDoc.put("jti", jti);
		tokenDoc.put("issuedAt", LocalDateTime.now().toString());
		tokenDoc.put("accessExpiry", LocalDateTime.now().plusSeconds(900).toString());
		tokenDoc.put("refreshExpiry", LocalDateTime.now().plusDays(7).toString());
		tokenDoc.put("isValid", true);
		tokenDoc.put("isLoggedOut", false);
		tokenDoc.put("createdAt", LocalDateTime.now().toString());
		tokenDoc.put("updatedAt", LocalDateTime.now().toString());

		repo.insert("auth_tokens", tokenDoc);
	}

    /* ── AUTH_REFRESH_TOKEN ─────────────────────────────────────────────── */
	public NktOperationHandler refreshToken() {

		return (data, userId, repo, mapper, def) -> {
			
			String rt = str(data, "refreshToken");
			
			String jti = jwt.extractAllClaims(rt).getId();
			
			Map<String, Object> tokenDoc = repo
					.findOneByCriteria("auth_tokens", Map.of("jti", jti, "isValid", true, "isLoggedOut", false)).orElse(null);

		            if (tokenDoc == null) {
		                return json(mapper, Map.of(
		                        "statusCode", "N400",
		                        "statusDesc", "Invalid session"
		                ));
		            }
			
			if (!jwt.isTokenValid(rt))
				throw new RuntimeException("Invalid refresh token");
			
			repo.updateFirst("auth_tokens", Map.of("jti", jti),
					Map.of("isValid", false, "updatedAt", LocalDateTime.now().toString()));
			
			String uid = jwt.extractUserId(rt);

			String tableName = tokenDoc.get("userType") + def.getCollection();

			Map<String, Object> user = repo.findById(tableName, uid).get();
//							.orElseThrow(() -> new RuntimeException("User not found"));

			if (CollectionUtils.isEmpty(user)) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "User not found"));
			}

			String utyp = jwt.extractUserType(rt);
			String jti_new = java.util.UUID.randomUUID().toString();
			String accessToken = jwt.generateAccessToken(uid, utyp, jti_new);
			String refreshToken = jwt.generateRefreshToken(uid, jti);

			insertToken(repo, uid, utyp, jti, accessToken, refreshToken);
			
			return json(mapper, Map.of("data", Map.of("accessToken", accessToken, "refreshToken", refreshToken,
					"userId", uid, "userType", utyp, "statusCode", "N200", "statusDesc", "Success")));
	        
		};
	}

    /* ── AUTH_ENROL_BIOMETRIC ───────────────────────────────────────────── */
    public NktOperationHandler enrolBiometric() {
        return (data, userId, repo, mapper, def) -> {
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
		
		return (data, userId, repo, mapper, def) -> {
			String deviceId = str(data, "deviceId");
			String hash = str(data, "biometricToken");

			Map<String, Object> tok = repo
					.findOneByCriteria("biometricTokens", Map.of("deviceId", deviceId, "status", "ACTIVE"))
					.orElseThrow(() -> new RuntimeException("Device not enrolled"));

			if (!hash.equals(tok.get("tokenHash")))
				throw new RuntimeException("Biometric verification failed");

			String uid = tok.get("userId").toString();
			String utyp = tok.get("userType").toString();

			String jti = java.util.UUID.randomUUID().toString();

			String accessToken = jwt.generateAccessToken(uid, utyp, jti);
			String refreshToken = jwt.generateRefreshToken(uid, jti);
			
			String tableName = str(data, "userType") + def.getCollection();

			Map<String, Object> user = repo.findById(tableName, uid).get();
//			.orElseThrow(() -> new RuntimeException("User not found"));

			if (CollectionUtils.isEmpty(user)) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "User not found"));
			}

			return json(mapper, Map.of("accessToken", accessToken, "refreshToken", refreshToken));
		};
	}

    /* ── AUTH_LOGOUT ────────────────────────────────────────────────────── */
    public NktOperationHandler logout() {
//        return (data, userId, repo, mapper, def) ->
//                json(mapper, Map.of("message", "Logged out successfully"));
    	 return (data, userId, repo, mapper, def) -> {

    	        String token = str(data, "token");
    	        String jti  = jwt.extractUId(token);

    	        repo.updateFirst("auth_tokens",
    	                Map.of("jti", jti),
    	                Map.of(
    	                        "isValid", false,
    	                        "isLoggedOut", true,
    	                        "updatedAt", LocalDateTime.now().toString()
    	                ));

    	        return json(mapper, Map.of("message", "Logged out successfully"));
    	    };
    }
}
