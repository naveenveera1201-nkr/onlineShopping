package com.service.handlers;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    
    @Value("${otp.dummy}")
    private String dummy;

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

			String tableName = userType + def.getCollection();
			Map<String, Object> filter = Map.of("identifier", identifier, "userType", userType,
					"status", "ACTIVE");

			boolean exists = repo.exists(tableName, filter);

			if ("register".equalsIgnoreCase(purpose) && exists) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", userType + " user already exists"));
			}

			if (!"register".equalsIgnoreCase(purpose) && !exists) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc",
						userType + " User not found. Please register first"));
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
			return json(mapper, Map.of("statusCode", "N200","message", "OTP sent successfully", "expiresInSeconds", 300));
		};
	}

    /* ── AUTH_VERIFY_OTP ────────────────────────────────────────────────── */
	public NktOperationHandler verifyOtp() {
        return (data, userId, repo, mapper, def) -> {
            String identifier = str(data, "identifier");
            String otp = str(data, "otp");
            String purpose = str(data, "purpose");
            String userType = str(data, "userType");

            Map<String, Object> rec = repo.findOneByCriteria("otp_records",
                    Map.of("identifier", identifier, "used", false, "userType", userType)).orElse(null);

            if (rec == null) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "OTP not found or expired"
                ));
            }

            String tableName = rec.get("userType") + def.getCollection();

            LocalDateTime createdAt = LocalDateTime.parse(rec.get("createdAt").toString());

            LocalDateTime expiryTime = createdAt.plusMinutes(otpExpiration != null ? Long.parseLong(otpExpiration) : 3);

            if (!dummy.equals("Y")) {

                if (LocalDateTime.now().isAfter(expiryTime)) {

                    // mark as used/expired (optional but recommended)
                    repo.updateFirst("otp_records", Map.of("identifier", identifier, "used", false), Map.of("used", true));

                    return json(mapper, Map.of("statusCode", "N400", "statusDesc", "OTP expired"));
                }

                if (!otp.equals(rec.get("otp"))) {

                    int attempts = (int) rec.get("attempts") + 1;

                    repo.updateFirst("otp_records", Map.of("identifier", identifier, "used", false),
                            Map.of("attempts", attempts));

                    return json(mapper, Map.of("messsage", "Invalid OTP", "status", "Failed", "statusCode", "N400"));
                }

            } else {
                if (identifier.trim().equalsIgnoreCase("9876543210") && otp.trim().equals("1234")) {
                    log.info("Dummy OTP {} verified for {}", otp, identifier);
                } else if (otp.equals(identifier.substring(Math.max(0, identifier.length() - 4)))) {
                    log.info("OTP {} verified for {}", otp, identifier);
                } else {
                    return json(mapper, Map.of("messsage", "Invalid OTP", "status", "Failed", "statusCode", "N400"));
                }
            }

            repo.updateFirst("otp_records",
                    Map.of("identifier", identifier, "used", false),
                    Map.of("used", true, "verifiedAt", LocalDateTime.now().toString()));

            Map<String, Object> user;
            Map<String, Object> store = null; 

            if ("register".equals(purpose)) {
                user = new LinkedHashMap<>();
                user.put("identifier", identifier);
                user.put("identifierType", rec.get("identifierType"));
                user.put("name", str(data, "name"));
                user.put("email", str(data, "email"));
                user.put("userType", rec.get("userType"));
                user.put("status", "ACTIVE");
                user.put("addresses", new ArrayList<>());
                user.put("favouriteStoreIds", new ArrayList<>());
                user.put("createdAt", LocalDateTime.now().toString());
                user.put("createdBy", "SYSTEM");

                user = repo.insert(tableName, user);
            } else {
                user = repo.findOne(tableName, "identifier", identifier).orElse(null);
                if (user == null) {
                    return json(mapper, Map.of("statusCode", "N400", "messsage", "User not found", "status", "Failed"));
                }
            }
            
			if ("business".equalsIgnoreCase(userType)) {

				String phoneNumber = normalizeIndianPhoneNumber(identifier);

				store = repo.findOne("stores", "identifier", phoneNumber).orElse(null);

				if (store == null) {
					return json(mapper, Map.of("statusCode", "N400", "message", "Store not found for business user",
							"status", "Failed"));
				}
			}

            String uid = user.get("id").toString();
            String utyp = user.get("userType").toString();
            String jti = java.util.UUID.randomUUID().toString();

            String accessToken = jwt.generateAccessToken(uid, utyp, jti);
            String refreshToken = jwt.generateRefreshToken(uid, jti);

            insertToken(repo, uid, utyp, jti, accessToken, refreshToken);

            return json(mapper, Map.of("data", Map.of(
                    "accessToken", accessToken,
                    "refreshToken", refreshToken,
                    "storeId", store != null ? store.get("storeId") : "",
                    "userId", uid,
                    "userType", utyp,
                    "statusCode", "N200",
                    "statusDesc", "Success")));

        };
    }
	
	private String normalizeIndianPhoneNumber(String identifier) {

		if (identifier == null || identifier.isBlank()) {
			return identifier;
		}

		// Remove spaces, hyphens, brackets, etc.
		String phone = identifier.trim().replaceAll("[^0-9+]", "");

		// +91XXXXXXXXXX -> XXXXXXXXXX
		if (phone.startsWith("+91")) {
			phone = phone.substring(3);
		}

		// 91XXXXXXXXXX -> XXXXXXXXXX
		else if (phone.startsWith("91") && phone.length() == 12) {
			phone = phone.substring(2);
		}

		// 0XXXXXXXXXX -> XXXXXXXXXX
		else if (phone.startsWith("0") && phone.length() == 11) {
			phone = phone.substring(1);
		}

		return phone;
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
		tokenDoc.put("isDelete", false);
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

			return json(mapper, Map.of("statusCode", "N200","accessToken", accessToken, "refreshToken", refreshToken));
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

    	        return json(mapper, Map.of("statusCode", "N200","message", "Logged out successfully"));
    	    };
    }
    
    /* ── DELETE USER ────────────────────────────────────────────────────── */
	public NktOperationHandler deleteUser() {

		return (data, userId, repo, mapper, def) -> {

			log.info("Deleting user with ID: {}", userId);

			// ✅ Validate user
			Map<String, Object> user = repo.findById(def.getCollection(), userId).orElse(null);
			
			if (user == null) {
				return json(mapper, Map.of("statusCode", "N404", "statusDesc", "User not found"));
			}
			
			if (user != null && "DELETED".equals(user.get("status"))) {
				return json(mapper, Map.of("statusCode", "N400", "statusDesc", "User already deleted"));
			}
			
//			repo.deleteById(def.getCollection(), userId);
			
			repo.deleteAll("auth_tokens", Map.of("userId", userId));
			
			repo.deleteAll("wishlist", Map.of("userId", userId));
			

			// Soft delete user
//			
//			repo.updateFirst("auth_tokens", Map.of("userId", userId), Map.of("isValid", false, "isLoggedOut", true,
//					"isDelete", true, "updatedAt", LocalDateTime.now().toString()));
//
			repo.updateFirst(def.getCollection(), Map.of("_id", userId),
					Map.of("isDeleted", true, "status", "DELETED", "updatedAt", LocalDateTime.now().toString(),"reason", str(data, "reason"),"remarks", str(data, "remarks")));
//			
//			repo.updateFirst("wishlist", Map.of("userId", userId), Map.of("isDeleted", true, "status", "DELETED", "updatedAt", LocalDateTime.now().toString()));
			
			log.info("Completed Deleting user with ID: {}", userId);

			return json(mapper, Map.of("statusCode", "N200", "message", "user account deleted successfully"));
		};
	}

	public NktOperationHandler generateStoreStaffToken() {

		return (data, userId, repo, mapper, def) -> {

			try {

				// ---------------------------------------------------------
				// 1. Validate Store ID
				// ---------------------------------------------------------
				String storeId = str(data, "storeId");

				if (storeId == null || storeId.isBlank()) {

					return json(mapper, Map.of("statusCode", "N400", "statusDesc", "Store ID is required"));
				}

				storeId = storeId.trim().toUpperCase();

				// ---------------------------------------------------------
				// 2. Validate Store
				// ---------------------------------------------------------
				Map<String, Object> store = repo.findOne(def.getCollection(), "storeId", storeId).orElse(null);

				if (store == null) {

					return json(mapper, Map.of("statusCode", "N400", "statusDesc", "Store not found"));
				}

				// ---------------------------------------------------------
				// 3. Generate unique Token ID
				// ---------------------------------------------------------
				String tokenId = generateStoreTokenId(repo);

				// ---------------------------------------------------------
				// 4. Generate Store Staff Token
				// ---------------------------------------------------------
				String authToken = generateStoreStaffAuthToken(repo);

				// ---------------------------------------------------------
				// 5. Create token document
				// ---------------------------------------------------------
				LocalDateTime now = LocalDateTime.now();

				Map<String, Object> tokenDoc = new LinkedHashMap<>();

				tokenDoc.put("tokenId", tokenId);
				tokenDoc.put("storeId", storeId);

				// Exact token
				tokenDoc.put("authToken", authToken);

				// Initially available
				tokenDoc.put("employeeId", null);
				tokenDoc.put("employeeName", null);
				tokenDoc.put("deviceId", null);

				tokenDoc.put("status", "A");

				tokenDoc.put("isMapped", false);
				tokenDoc.put("isActive", true);
				tokenDoc.put("isDelete", false);

				tokenDoc.put("createdBy", userId);
				tokenDoc.put("createdAt", now.toString());

				tokenDoc.put("updatedBy", userId);
				tokenDoc.put("updatedAt", now.toString());

				// ---------------------------------------------------------
				// 6. Insert into store_staff_tokens
				// ---------------------------------------------------------
				Map<String, Object> savedToken = repo.insert("store_staff_tokens", tokenDoc);

				// ---------------------------------------------------------
				// 7. Response
				// ---------------------------------------------------------
				return json(mapper,
						Map.of("data",
								Map.of("tokenId", savedToken.get("tokenId"), "storeId", savedToken.get("storeId"),
										"token", savedToken.get("authToken"), "status", savedToken.get("status")),
								"statusCode", "N200", "statusDesc", "Store Staff token generated successfully"));

			} catch (Exception e) {

				e.printStackTrace();

				return json(mapper, Map.of("statusCode", "N500", "statusDesc", "Unable to generate Store Staff token"));
			}
		};
	}

	private String generateStoreTokenId(NktDynamicRepository repo) {

		String tokenId;

		do {

			tokenId = "STT_" + String.format("%06d", ThreadLocalRandom.current().nextInt(1, 999999));

		} while (repo.findOne("store_staff_tokens", "tokenId", tokenId).isPresent());

		return tokenId;
	}

	private String generateStoreStaffAuthToken(NktDynamicRepository repo) {

		String token;

		do {

			token = generateRandomToken(12);

		} while (repo.findOne("store_staff_tokens", "authToken", token).isPresent());

		return token;
	}

	private String generateRandomToken(int length) {

		final String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "abcdefghijklmnopqrstuvwxyz" + "0123456789";

		SecureRandom random = new SecureRandom();

		StringBuilder token = new StringBuilder(length);

		for (int i = 0; i < length; i++) {

			token.append(characters.charAt(random.nextInt(characters.length())));
		}

		return token.toString();
	}

//	public NktOperationHandler storeStaffLogin() {
//
//		return (data, userId, repo, mapper, def) -> {
//
//			try {
//
//				String storeId = str(data, "storeId");
//
//				String token = str(data, "authToken");
//
//				String deviceId = str(data, "deviceId");
//
//				// ---------------------------------------------------------
//				// 1. Required fields
//				// ---------------------------------------------------------
//				if (storeId == null || storeId.isBlank() || token == null || token.isEmpty() || deviceId == null
//						|| deviceId.isBlank()) {
//
//					return json(mapper,
//							Map.of("statusCode", "N400", "statusDesc", "Store ID, Token and Device ID are required"));
//				}
//
//				storeId = storeId.trim().toUpperCase();
//
//				// DO NOT modify token
//				// DO NOT trim token
//				// DO NOT uppercase token
//
//				// ---------------------------------------------------------
//				// 2. Find token
//				// ---------------------------------------------------------
//				Map<String, Object> tokenDoc = repo
//						.findOneByCriteria("store_staff_tokens", Map.of("storeId", storeId, "authToken", token))
//						.orElse(null);
//
//				// ---------------------------------------------------------
//				// 3. Invalid Store ID / Token
//				// ---------------------------------------------------------
//				if (tokenDoc == null) {
//
//					return json(mapper, Map.of("statusCode", "N400", "errorCode", "TOKEN_NOT_FOUND", "statusDesc",
//							"Invalid Store ID or Token."));
//				}
//
//				// ---------------------------------------------------------
//				// 4. Token active check
//				// ---------------------------------------------------------
//				if (tokenDoc.get("status").equals("Active")) {
//
//					return json(mapper, Map.of("statusCode", "N400", "errorCode", "TOKEN_INACTIVE", "statusDesc",
//							"Invalid Store ID or Token."));
//				}
//
//				// ---------------------------------------------------------
//				// 5. Token already mapped
//				// ---------------------------------------------------------
//				if (Boolean.TRUE.equals(tokenDoc.get("isMapped"))) {
//
//					return json(mapper, Map.of("statusCode", "N400", "errorCode", "TOKEN_ALREADY_MAPPED", "statusDesc",
//							"This token is already registered."));
//				}
//
//				// ---------------------------------------------------------
//				// 6. Device already registered
//				// ---------------------------------------------------------
//				Optional<Map<String, Object>> existingDevice = repo.findOneByCriteria("store_staff_tokens",
//						Map.of("deviceId", deviceId, "isMapped", true));
//
//				if (existingDevice.isPresent()) {
//
//					return json(mapper, Map.of("statusCode", "N400", "errorCode", "DEVICE_ALREADY_REGISTERED",
//							"statusDesc", "This device is already registered."));
//				}
//
//				// ---------------------------------------------------------
//				// 7. Create Employee
//				// ---------------------------------------------------------
//				String employeeId = "EMP_" + UUID.randomUUID();
//
//				String employeeName = str(data, "employeeName");
//
//				LocalDateTime now = LocalDateTime.now();
//
//				Map<String, Object> employee = new LinkedHashMap<>();
//
//				employee.put("employeeId", employeeId);
//				employee.put("storeId", storeId);
//				employee.put("employeeName", employeeName);
//				employee.put("userType", "employee");
//
//				employee.put("tokenId", tokenDoc.get("tokenId"));
//
//				employee.put("deviceId", deviceId);
//
//				employee.put("status", "Active");
//
//				employee.put("createdAt", now.toString());
//				employee.put("createdBy", "SYSTEM");
//
//				employee = repo.insert("store_staff_employees", employee);
//
//				// ---------------------------------------------------------
//				// 8. Map Token -> Employee -> Device
//				// ---------------------------------------------------------
//				repo.updateFirst("store_staff_tokens",
//
//						Map.of("tokenId", tokenDoc.get("tokenId"),
//
//								"isMapped", false,
//
//								"isActive", true),
//
//						Map.of("employeeId", employeeId,
//
//								"employeeName", employeeName,
//
//								"deviceId", deviceId,
//
//								"isMapped", true,
//
//								"status", "ASSIGNED",
//
//								"updatedAt", now.toString()));
//
//				// ---------------------------------------------------------
//				// 9. Generate JWT session
//				// ---------------------------------------------------------
//				String uid = employee.get("employeeId").toString();
//
//				String userType = "employee";
//
//				String tokenId = tokenDoc.get("tokenId").toString();
//
//				String jti = UUID.randomUUID().toString();
//
//				String accessToken = jwt.generateAccessToken(uid, userType, jti);
//
//				String refreshToken = jwt.generateRefreshToken(uid, jti);
//
//				// ---------------------------------------------------------
//				// 10. Insert JWT session
//				// ---------------------------------------------------------
//				insertEmployeeAuthToken(repo, uid, userType, storeId, tokenId, accessToken, refreshToken, jti);
//
//				// ---------------------------------------------------------
//				// 11. Response
//				// ---------------------------------------------------------
//				return json(mapper,
//						Map.of("data",
//								Map.of("accessToken", accessToken, "refreshToken", refreshToken, "userId", uid,
//										"userType", userType, "storeId", storeId, "tokenId", tokenId),
//								"statusCode", "N200", "statusDesc", "Success"));
//
//			} catch (Exception e) {
//
//				e.printStackTrace();
//
//				return json(mapper, Map.of("statusCode", "N500", "statusDesc", "Store Staff login failed"));
//			}
//		};
//	}

	public NktOperationHandler storeStaffLogin() {

		return (data, userId, repo, mapper, def) -> {

			try {

				String storeId = str(data, "storeId");
				String authToken = str(data, "authToken");
				String deviceId = str(data, "deviceId");
				String purpose = str(data, "purpose");

				// ---------------------------------------------------------
				// 1. Required fields
				// ---------------------------------------------------------
				if (storeId == null || storeId.isBlank() || authToken == null || authToken.isEmpty() || deviceId == null
						|| deviceId.isBlank() || purpose == null || purpose.isBlank()) {

					return json(mapper, Map.of("data",Map.of("statusCode", "N400", "statusDesc",
							"Store ID, Token, Device ID and Purpose are required")));
				}

				storeId = storeId.trim().toUpperCase();
				purpose = purpose.trim().toLowerCase();

				// ---------------------------------------------------------
				// 2. Validate purpose
				// ---------------------------------------------------------
				if (!"register".equals(purpose) && !"login".equals(purpose)) {

					return json(mapper, Map.of("statusCode", "N400", "statusDesc",
							"Invalid purpose. Allowed values are register or login"));
				}

				// ---------------------------------------------------------
				// 3. Find Store Staff Token
				// ---------------------------------------------------------
				Map<String, Object> tokenDoc = repo
						.findOneByCriteria("store_staff_tokens", Map.of("storeId", storeId, "authToken", authToken))
						.orElse(null);

				if (tokenDoc == null) {

					return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "TOKEN_NOT_FOUND", "statusDesc",
							"Invalid Store ID or Token.")));
				}

				// ---------------------------------------------------------
				// 4. Token status
				// ---------------------------------------------------------
				String tokenStatus = tokenDoc.get("status") != null ? tokenDoc.get("status").toString() : null;

				if ("INACTIVE".equalsIgnoreCase(tokenStatus) || "REVOKED".equalsIgnoreCase(tokenStatus)) {

					return json(mapper,Map.of("data", Map.of("statusCode", "N400", "errorCode", "TOKEN_INACTIVE", "statusDesc",
							"Invalid Store ID or Token.")));
				}

				// =========================================================
				// PURPOSE = REGISTER
				// =========================================================
				if ("register".equals(purpose)) {

					return registerStoreStaff(data, storeId, authToken, deviceId, tokenDoc, repo, mapper);
				}

				// =========================================================
				// PURPOSE = LOGIN
				// =========================================================
				return loginStoreStaff(data, storeId, authToken, deviceId, tokenDoc, repo, mapper);

			} catch (Exception e) {

				e.printStackTrace();

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "statusDesc", "Store Staff authentication failed")));
			}
		};
	}

	private String registerStoreStaff(Map<String, Object> data, String storeId, String authToken, String deviceId,
			Map<String, Object> tokenDoc, NktDynamicRepository repo, ObjectMapper mapper) {

		try {

			// ---------------------------------------------------------
			// Token must be available during registration
			// ---------------------------------------------------------
			if (Boolean.TRUE.equals(tokenDoc.get("isMapped"))) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "TOKEN_ALREADY_MAPPED", "statusDesc",
						"This token is already registered.")));
			}

			// ---------------------------------------------------------
			// Device must not already be registered
			// ---------------------------------------------------------
			Optional<Map<String, Object>> existingDevice = repo.findOneByCriteria("store_staff_tokens",
					Map.of("deviceId", deviceId, "isMapped", true));

			if (existingDevice.isPresent()) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "DEVICE_ALREADY_REGISTERED", "statusDesc",
						"This device is already registered.")));
			}

			// ---------------------------------------------------------
			// Employee
			// ---------------------------------------------------------
			String employeeId = "EMP_" + UUID.randomUUID();

			String employeeName = str(data, "employeeName");

			LocalDateTime now = LocalDateTime.now();

			Map<String, Object> employee = new LinkedHashMap<>();

			employee.put("employeeId", employeeId);
			employee.put("storeId", storeId);
			employee.put("employeeName", employeeName);
			employee.put("userType", "employee");

			employee.put("tokenId", tokenDoc.get("tokenId"));

			employee.put("deviceId", deviceId);
			employee.put("status", "Active");

			employee.put("createdAt", now.toString());

			employee.put("createdBy", "SYSTEM");

			employee = repo.insert("store_staff_employees", employee);

			// ---------------------------------------------------------
			// Update Store Staff Token
			// ---------------------------------------------------------
			repo.updateFirst("store_staff_tokens",

					Map.of("tokenId", tokenDoc.get("tokenId"),

							"isMapped", false),

					Map.of("employeeId", employeeId,

							"employeeName", employeeName,

							"deviceId", deviceId,

							"isMapped", true,

							"status", "ASSIGNED",

							"updatedAt", now.toString()));

			// ---------------------------------------------------------
			// Generate JWT
			// ---------------------------------------------------------
			String uid = employee.get("employeeId").toString();

			String userType = "employee";

			String tokenId = tokenDoc.get("tokenId").toString();

			String jti = UUID.randomUUID().toString();

			String accessToken = jwt.generateAccessToken(uid, userType, jti);

			String refreshToken = jwt.generateRefreshToken(uid, jti);

			// ---------------------------------------------------------
			// Insert auth_tokens
			// ---------------------------------------------------------
			insertEmployeeAuthToken(repo, uid, userType, storeId, tokenId, accessToken, refreshToken, jti);

			return json(mapper,
					Map.of("data",
							Map.of("accessToken", accessToken, "refreshToken", refreshToken, "userId", uid, "userType",
									userType, "storeId", storeId, "tokenId", tokenId),
							"statusCode", "N200", "statusDesc", "Store Staff registered successfully"));

		} catch (Exception e) {

			e.printStackTrace();

			return json(mapper,Map.of("data", Map.of("statusCode", "N400", "statusDesc", "Store Staff registration failed")));
		}
	}

	private String loginStoreStaff(Map<String, Object> data, String storeId, String authToken, String deviceId,
			Map<String, Object> tokenDoc, NktDynamicRepository repo, ObjectMapper mapper) {

		try {

			// ---------------------------------------------------------
			// Token must already be mapped
			// ---------------------------------------------------------
			if (!Boolean.TRUE.equals(tokenDoc.get("isMapped"))) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "TOKEN_NOT_REGISTERED", "statusDesc",
						"Store Staff token is not registered.")));
			}

			// ---------------------------------------------------------
			// Validate mapped device
			// ---------------------------------------------------------
			String mappedDeviceId = tokenDoc.get("deviceId") != null ? tokenDoc.get("deviceId").toString() : null;

			if (!deviceId.equals(mappedDeviceId)) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "DEVICE_MISMATCH", "statusDesc",
						"Invalid Store ID or Token.")));
			}

			// ---------------------------------------------------------
			// Employee ID
			// ---------------------------------------------------------
			String employeeId = tokenDoc.get("employeeId") != null ? tokenDoc.get("employeeId").toString() : null;

			if (employeeId == null) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "EMPLOYEE_NOT_FOUND", "statusDesc",
						"Store Staff is not registered.")));
			}

			// ---------------------------------------------------------
			// Find employee
			// ---------------------------------------------------------
			Map<String, Object> employee = repo.findOne("store_staff_employees", "employeeId", employeeId).orElse(null);

			if (employee == null) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "EMPLOYEE_NOT_FOUND", "statusDesc",
						"Store Staff is not registered.")));
			}

			// ---------------------------------------------------------
			// Employee status
			// ---------------------------------------------------------
			String employeeStatus = employee.get("status") != null ? employee.get("status").toString() : null;

			if (!"Active".equalsIgnoreCase(employeeStatus)) {

				return json(mapper, Map.of("data",Map.of("statusCode", "N400", "errorCode", "EMPLOYEE_INACTIVE", "statusDesc",
						"Store Staff account is inactive.")));
			}

			// ---------------------------------------------------------
			// Generate new JWT session
			// ---------------------------------------------------------
			String userType = "employee";

			String tokenId = tokenDoc.get("tokenId").toString();

			String jti = UUID.randomUUID().toString();

			String accessToken = jwt.generateAccessToken(employeeId, userType, jti);

			String refreshToken = jwt.generateRefreshToken(employeeId, jti);

			// ---------------------------------------------------------
			// Insert new authentication session
			// ---------------------------------------------------------
			
			repo.updateMany(
				    "auth_tokens",
				    Map.of(
				        "userId", employeeId,
				        "tokenId", tokenId,
				        "isValid", true
				    ),
				    Map.of(
				        "isValid", false,
				        "isLoggedOut", true,
				        "updatedAt", LocalDateTime.now().toString()
				    )
				);
			insertEmployeeAuthToken(repo, employeeId, userType, storeId, tokenId, accessToken, refreshToken, jti);

			// ---------------------------------------------------------
			// Update last login
			// ---------------------------------------------------------
			repo.updateFirst("store_staff_employees",

					Map.of("employeeId", employeeId),

					Map.of("lastLoginAt", LocalDateTime.now().toString(),

							"updatedAt", LocalDateTime.now().toString()));

			// ---------------------------------------------------------
			// Response
			// ---------------------------------------------------------
			return json(mapper,
					Map.of("data",
							Map.of("accessToken", accessToken, "refreshToken", refreshToken, "userId", employeeId,
									"userType", userType, "storeId", storeId, "tokenId", tokenId),
							"statusCode", "N200", "statusDesc", "Login successful"));

		} catch (Exception e) {

			e.printStackTrace();

			return json(mapper, Map.of("statusCode", "N500", "statusDesc", "Store Staff login failed"));
		}
	}

	public void insertEmployeeAuthToken(NktDynamicRepository repo, String uid, String userType, String storeId,
			String tokenId, String accessToken, String refreshToken, String jti) {

		LocalDateTime now = LocalDateTime.now();

		Map<String, Object> tokenDoc = new LinkedHashMap<>();

		tokenDoc.put("userId", uid);
		tokenDoc.put("userType", userType);

		// Store relationship
		tokenDoc.put("storeId", storeId);

		// Store Staff generated token relationship
		tokenDoc.put("tokenId", tokenId);

		// JWT
		tokenDoc.put("accessToken", accessToken);
		tokenDoc.put("refreshToken", refreshToken);
		tokenDoc.put("jti", jti);

		tokenDoc.put("issuedAt", now.toString());
		
		tokenDoc.put("accessExpiry", LocalDateTime.now().plusSeconds(900).toString());
		tokenDoc.put("refreshExpiry", LocalDateTime.now().plusDays(7).toString());


		tokenDoc.put("isValid", true);
		tokenDoc.put("isDelete", false);
		tokenDoc.put("isLoggedOut", false);

		tokenDoc.put("createdAt", now.toString());

		tokenDoc.put("updatedAt", now.toString());

		repo.insert("auth_tokens", tokenDoc);
	}
	
	public NktOperationHandler inactiveStoreStaffToken() {

	    return (data, userId, repo, mapper, def) -> {

	        try {

	            String tokenId = str(data, "tokenId");

	            if (tokenId == null || tokenId.isBlank()) {
	                return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "statusDesc", "Token ID is required"
	                ));
	            }

	            Map<String, Object> tokenDoc = repo
	                    .findOneByCriteria(
	                            "store_staff_tokens",
	                            Map.of("tokenId", tokenId)
	                    )
	                    .orElse(null);

	            if (tokenDoc == null) {
	                return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "errorCode", "TOKEN_NOT_FOUND",
	                        "statusDesc", "Token not found."
	                ));
	            }

	            if (Boolean.FALSE.equals(tokenDoc.get("isActive"))) {
	                return json(mapper, Map.of(
	                        "statusCode", "N400",
	                        "errorCode", "TOKEN_INACTIVE",
	                        "statusDesc", "Token is already inactive."
	                ));
	            }

	            LocalDateTime now = LocalDateTime.now();

	            repo.updateFirst(
	                    "store_staff_tokens",
	                    Map.of(
	                            "tokenId", tokenId,
	                            "isActive", true
	                    ),
	                    Map.of(
	                            "isActive", false,
	                            "status", "INACTIVE",
	                            "updatedAt", now.toString(),
	                            "updatedBy", userId
	                    )
	            );

	            return json(mapper, Map.of(
	                    "statusCode", "N200",
	                    "statusDesc", "Token inactive successfully.",
	                    "data", Map.of(
	                            "tokenId", tokenId,
	                            "isActive", false,
	                            "status", "INACTIVE"
	                    )
	            ));

	        } catch (Exception e) {

	            e.printStackTrace();

	            return json(mapper, Map.of(
	                    "statusCode", "N500",
	                    "statusDesc", "Failed to inactive token"
	            ));
	        }
	    };
	}
}
