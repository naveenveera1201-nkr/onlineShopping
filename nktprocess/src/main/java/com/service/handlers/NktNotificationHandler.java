package com.service.handlers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.fcm.FcmSendResult;
import com.repository.NktDynamicRepository;
import com.service.FirebaseNotificationService;
import com.service.NotificationDispatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles all Firebase Cloud Messaging (FCM) operations.
 *
 * Keys: FCM_REGISTER_DEVICE, FCM_SEND_NOTIFICATION,
 *       FCM_SEND_GROUP_NOTIFICATION, FCM_SEND_BATCH_NOTIFICATION,
 *       FCM_MARK_NOTIFICATION_READ
 *
 * MongoDB collection: {@code user_devices} — see the class-level doc on
 * {@link #registerDevice()} for the exact document shape. This class owns
 * request validation, authorisation and target resolution (who gets
 * notified); the actual Mongo query + Firebase send + invalid-token
 * bookkeeping lives in {@link NotificationDispatchService}, which this
 * class only calls — never talks to the Firebase Admin SDK directly.
 *
 * Security note: FCM_SEND_NOTIFICATION and FCM_SEND_BATCH_NOTIFICATION are
 * restricted to {@code business}/{@code employee} roles via process-flow.json
 * (allowedRoles), so a customer can never call them. FCM_SEND_GROUP_NOTIFICATION
 * is further restricted at the process level to {@code business} only, and at
 * the handler level below every group type additionally validates that the
 * caller owns the {@code storeId} being targeted — except {@code CUSTOMERS},
 * which is the one truly platform-wide broadcast and is gated by
 * {@link #callerIsPlatformAdmin} (see that method's Javadoc for why, and what
 * a proper fix looks like once a real ADMIN role exists).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NktNotificationHandler {

    private static final String DEVICES_COLLECTION = "user_devices";

    private final NotificationDispatchService dispatch;

    // ── shared helpers (mirrors the str()/json() convention used by every other handler) ──

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

    private String err(ObjectMapper mapper, String statusCode, String errorCode, String statusDesc) {
        return json(mapper, Map.of("statusCode", statusCode, "errorCode", errorCode, "statusDesc", statusDesc));
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(ObjectMapper mapper, Map<String, Object> d, String k) {
        Object v = d.get(k);
        try {
            if (v instanceof String s) {
                return mapper.readValue(s, List.class);
            } else if (v instanceof List<?> l) {
                return l.stream()
                        .filter(e -> e instanceof Map)
                        .map(e -> (Map<String, Object>) e)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid messages format", e);
        }
        return List.of();
    }

    /* ── FCM_REGISTER_DEVICE ────────────────────────────────────────────
     *
     * user_devices document shape:
     * {
     *   "userId":         "...",              // employeeId for employee, mongo _id for customer/business
     *   "userType":       "customer" | "business" | "employee",
     *   "storeId":        "ST011",             // resolved server-side; null for customer
     *   "deviceId":       "DEVICE_001",
     *   "fcmToken":       "xxxxx",
     *   "platform":       "ANDROID" | "IOS",
     *   "appVersion":     "1.0.0",
     *   "isActive":       true,
     *   "inactiveReason": null,                // e.g. "FCM_TOKEN_INVALID" once deactivated
     *   "createdAt":      "...",
     *   "updatedAt":      "...",
     *   "lastUsedAt":     "..."
     * }
     *
     * Keyed by (userId, deviceId) — a returning device updates its existing
     * row (new token, refreshed timestamps) instead of creating a duplicate,
     * and a single user can hold many device rows at once (multi-device).
     */
    public NktOperationHandler registerDevice() {
        return (data, userId, repo, mapper, def) -> {

            if (userId == null) {
                return err(mapper, "N401", "UNAUTHENTICATED", "Authentication required");
            }

            String deviceId = str(data, "deviceId");
            String fcmToken = str(data, "fcmToken");
            String platform = str(data, "platform");
            String appVersion = str(data, "appVersion");
            String userType = str(data, "userType"); // injected by NktCoreService from the JWT

            if (deviceId == null || deviceId.isBlank()) {
                return err(mapper, "N400", "DEVICE_ID_REQUIRED", "deviceId is required");
            }
            if (fcmToken == null || fcmToken.isBlank()) {
                return err(mapper, "N400", "FCM_TOKEN_REQUIRED", "fcmToken is required");
            }
            if (platform == null || platform.isBlank()) {
                return err(mapper, "N400", "PLATFORM_REQUIRED", "platform is required");
            }

            String storeId = resolveStoreId(userType, userId, repo);
            String now = LocalDateTime.now().toString();

            Map<String, Object> existing = repo
                    .findOneByCriteria(DEVICES_COLLECTION, Map.of("userId", userId, "deviceId", deviceId))
                    .orElse(null);

            if (existing != null) {
                repo.updateFirst(DEVICES_COLLECTION,
                        Map.of("userId", userId, "deviceId", deviceId),
                        mapOf(
                                "fcmToken", fcmToken,
                                "platform", platform.toUpperCase(),
                                "appVersion", appVersion,
                                "storeId", storeId,
                                "userType", userType,
                                "isActive", true,
                                "inactiveReason", null,
                                "updatedAt", now,
                                "lastUsedAt", now
                        ));
                log.info("FCM device updated userId={} deviceId={} token={}",
                        userId, deviceId, FirebaseNotificationService.mask(fcmToken));
                return json(mapper, Map.of(
                        "data", Map.of("deviceId", deviceId, "registered", true, "updated", true),
                        "statusCode", "N200", "statusDesc", "Device updated successfully"));
            }

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("userId", userId);
            doc.put("userType", userType);
            doc.put("storeId", storeId);
            doc.put("deviceId", deviceId);
            doc.put("fcmToken", fcmToken);
            doc.put("platform", platform.toUpperCase());
            doc.put("appVersion", appVersion);
            doc.put("isActive", true);
            doc.put("inactiveReason", null);
            doc.put("createdAt", now);
            doc.put("updatedAt", now);
            doc.put("lastUsedAt", now);
            repo.insert(DEVICES_COLLECTION, doc);

            log.info("FCM device registered userId={} deviceId={} token={}",
                    userId, deviceId, FirebaseNotificationService.mask(fcmToken));

            return json(mapper, Map.of(
                    "data", Map.of("deviceId", deviceId, "registered", true, "updated", false),
                    "statusCode", "N200", "statusDesc", "Device registered successfully"));
        };
    }

    /* ── FCM_SEND_NOTIFICATION ─────────────────────────────────────────── */
    public NktOperationHandler sendNotification() {
        return (data, userId, repo, mapper, def) -> {

            String targetUserId = str(data, "targetUserId");
            String title = str(data, "title");
            String body = str(data, "body");
            String notificationType = str(data, "notificationType");
            String orderId = str(data, "orderId");
            String storeId = str(data, "storeId");
            String screen = str(data, "screen");
            String callerUserType = str(data, "userType");

            if (targetUserId == null || targetUserId.isBlank()) {
                return err(mapper, "N400", "TARGET_USER_REQUIRED", "targetUserId is required");
            }
            if (title == null || title.isBlank() || body == null || body.isBlank()) {
                return err(mapper, "N400", "TITLE_BODY_REQUIRED", "title and body are required");
            }
            if (!authoriseSingleTarget(callerUserType, userId, storeId, repo)) {
                return err(mapper, "N403", "FORBIDDEN", "Not allowed to notify this user");
            }

            Map<String, Object> extra = new LinkedHashMap<>();
            if (orderId != null) extra.put("orderId", orderId);
            if (storeId != null) extra.put("storeId", storeId);
            if (screen != null) extra.put("screen", screen);

            if (dispatch.activeDevicesFor(targetUserId).isEmpty()) {
                return err(mapper, "N404", "NO_ACTIVE_DEVICE", "No active device found for user");
            }

            FcmSendResult result = dispatch.notifyUser(targetUserId, null, storeId, title, body,
                    notificationType, extra, true);

            return json(mapper, Map.of(
                    "data", Map.of(
                            "totalDevices", result.getTotalDevices(),
                            "success", result.getSuccess(),
                            "failed", result.getFailed()),
                    "statusCode", "N200", "statusDesc", "Notification processed"));
        };
    }

    /* ── FCM_SEND_GROUP_NOTIFICATION ───────────────────────────────────── */
    public NktOperationHandler sendGroupNotification() {
        return (data, userId, repo, mapper, def) -> {

            String groupType = str(data, "groupType");
            String title = str(data, "title");
            String body = str(data, "body");
            String notificationType = str(data, "notificationType");
            String storeId = str(data, "storeId");
            String orderId = str(data, "orderId");
            String screen = str(data, "screen");
            String callerUserType = str(data, "userType");

            if (groupType == null || groupType.isBlank()) {
                return err(mapper, "N400", "GROUP_TYPE_REQUIRED", "groupType is required");
            }
            if (title == null || title.isBlank() || body == null || body.isBlank()) {
                return err(mapper, "N400", "TITLE_BODY_REQUIRED", "title and body are required");
            }

            Map<String, Object> extra = new LinkedHashMap<>();
            if (orderId != null) extra.put("orderId", orderId);
            if (storeId != null) extra.put("storeId", storeId);
            if (screen != null) extra.put("screen", screen);

            FcmSendResult result;

            switch (groupType.toUpperCase()) {

                case "STORE_STAFF" -> {
                    if (storeId == null || storeId.isBlank()) {
                        return err(mapper, "N400", "STORE_ID_REQUIRED", "storeId is required for STORE_STAFF");
                    }
                    if (!callerOwnsStore(callerUserType, userId, storeId, repo)) {
                        return err(mapper, "N403", "FORBIDDEN", "Not allowed to notify this store's staff");
                    }
                    result = dispatch.notifyUsers(activeEmployeeRecipients(storeId, repo), title, body,
                            notificationType, extra, true);
                }

                case "STORE_OWNER" -> {
                    if (storeId == null || storeId.isBlank()) {
                        return err(mapper, "N400", "STORE_ID_REQUIRED", "storeId is required for STORE_OWNER");
                    }
                    if (!callerOwnsStore(callerUserType, userId, storeId, repo)) {
                        return err(mapper, "N403", "FORBIDDEN", "Not allowed to notify this store's owner");
                    }
                    result = dispatch.notifyUsers(ownerRecipient(storeId, repo), title, body,
                            notificationType, extra, true);
                }

                case "STORE" -> {
                    if (storeId == null || storeId.isBlank()) {
                        return err(mapper, "N400", "STORE_ID_REQUIRED", "storeId is required for STORE");
                    }
                    if (!callerOwnsStore(callerUserType, userId, storeId, repo)) {
                        return err(mapper, "N403", "FORBIDDEN", "Not allowed to notify this store");
                    }
                    List<NotificationDispatchService.Recipient> recipients =
                            new ArrayList<>(activeEmployeeRecipients(storeId, repo));
                    recipients.addAll(ownerRecipient(storeId, repo));
                    result = dispatch.notifyUsers(recipients, title, body, notificationType, extra, true);
                }

                case "CUSTOMERS" -> {
                    if (!callerIsPlatformAdmin(callerUserType, userId, repo)) {
                        return err(mapper, "N403", "FORBIDDEN",
                                "Only a platform administrator can broadcast to all customers");
                    }
                    // No per-user history for a platform-wide broadcast — avoids a write
                    // storm proportional to the whole customer base on every send.
                    result = dispatch.notifyUserType("customer", title, body, notificationType, extra, false);
                }

                default -> {
                    return err(mapper, "N400", "INVALID_GROUP_TYPE",
                            "groupType must be one of CUSTOMERS, STORE_STAFF, STORE_OWNER, STORE");
                }
            }

            return json(mapper, Map.of(
                    "data", Map.of(
                            "totalDevices", result.getTotalDevices(),
                            "success", result.getSuccess(),
                            "failed", result.getFailed(),
                            "invalidTokens", result.getInvalidTokens().size()),
                    "statusCode", "N200", "statusDesc", "Group notification processed"));
        };
    }

    /* ── FCM_SEND_BATCH_NOTIFICATION ───────────────────────────────────── */
    public NktOperationHandler sendBatchNotification() {
        return (data, userId, repo, mapper, def) -> {

            String callerUserType = str(data, "userType");
            List<Map<String, Object>> messages = listOfMaps(mapper, data, "messages");

            if (messages == null || messages.isEmpty()) {
                return err(mapper, "N400", "MESSAGES_REQUIRED", "messages array is required");
            }

            List<NotificationDispatchService.BatchTarget> targets = new ArrayList<>();

            for (Map<String, Object> m : messages) {
                String targetUserId = str(m, "targetUserId");
                String title = str(m, "title");
                String body = str(m, "body");
                if (targetUserId == null || title == null || body == null) {
                    continue; // skip malformed rows rather than failing the whole batch
                }

                String storeIdForTarget = str(m, "storeId");
                if (!authoriseSingleTarget(callerUserType, userId, storeIdForTarget, repo)) {
                    continue; // silently drop targets the caller isn't allowed to notify
                }

                Map<String, Object> extra = new LinkedHashMap<>();
                if (m.get("orderId") != null) extra.put("orderId", str(m, "orderId"));
                if (storeIdForTarget != null) extra.put("storeId", storeIdForTarget);
                if (m.get("screen") != null) extra.put("screen", str(m, "screen"));

                targets.add(new NotificationDispatchService.BatchTarget(
                        targetUserId, title, body, str(m, "notificationType"), extra));
            }

            if (targets.isEmpty()) {
                return err(mapper, "N400", "NO_VALID_TARGETS", "No valid/authorised targets in messages array");
            }

            FcmSendResult result = dispatch.notifyBatch(targets, true);

            return json(mapper, Map.of(
                    "data", Map.of(
                            "totalDevices", result.getTotalDevices(),
                            "success", result.getSuccess(),
                            "failed", result.getFailed(),
                            "invalidTokens", result.getInvalidTokens().size()),
                    "statusCode", "N200", "statusDesc", "Batch notification processed"));
        };
    }

    /* ── FCM_MARK_NOTIFICATION_READ ────────────────────────────────────
     *
     * Called by the client the instant the user actually opens/views a
     * notification. FCM itself has no delivered/read receipt, so this is
     * the ONLY signal that stops NotificationRetryScheduler from resending
     * it. Ownership is enforced in NotificationDispatchService.markAsRead —
     * a caller can only mark their own notifications read.
     */
    public NktOperationHandler markNotificationRead() {
        return (data, userId, repo, mapper, def) -> {

            if (userId == null) {
                return err(mapper, "N401", "UNAUTHENTICATED", "Authentication required");
            }

            String notificationId = str(data, "notificationId");
            if (notificationId == null || notificationId.isBlank()) {
                return err(mapper, "N400", "NOTIFICATION_ID_REQUIRED", "notificationId is required");
            }

            boolean updated = dispatch.markAsRead(notificationId, userId);
            if (!updated) {
                return err(mapper, "N404", "NOTIFICATION_NOT_FOUND",
                        "Notification not found or not owned by caller");
            }

            return json(mapper, Map.of(
                    "data", Map.of("notificationId", notificationId, "read", true),
                    "statusCode", "N200", "statusDesc", "Notification marked as read"));
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Authorisation / target-resolution helpers
    // ─────────────────────────────────────────────────────────────────────

    private String resolveStoreId(String userType, String userId, NktDynamicRepository repo) {
        if (userType == null) return null;
        return switch (userType.toLowerCase()) {
            case "employee" -> repo.findOne("store_staff_employees", "employeeId", userId)
                    .map(e -> str(e, "storeId")).orElse(null);
            case "business" -> repo.findOne("stores", "userId", userId)
                    .map(s -> str(s, "storeId")).orElse(null);
            case "customer" -> repo.findOne("stores", "userId", userId)
            .map(s -> str(s, "storeId")).orElse(null);
            default -> null;
        };
    }

    private boolean authoriseSingleTarget(String callerUserType, String callerUserId, String requestedStoreId,
                                           NktDynamicRepository repo) {
        if (callerUserType == null) return false;
        if (requestedStoreId == null || requestedStoreId.isBlank()) {
            // No storeId supplied — allow (allowedRoles in process-flow.json already
            // restricts this endpoint to business/employee; a customer can never reach it).
            return true;
        }
        String callerStoreId = resolveStoreId(callerUserType, callerUserId, repo);
        return requestedStoreId.equals(callerStoreId);
    }

    private boolean callerOwnsStore(String callerUserType, String callerUserId, String storeId,
                                     NktDynamicRepository repo) {
        if (callerUserType == null) return false;
        return storeId.equals(resolveStoreId(callerUserType, callerUserId, repo));
    }

    /**
     * Minimal, non-invasive admin gate.
     *
     * This codebase currently has no platform-admin role or login flow — only
     * {@code customer}, {@code business} and {@code employee} exist (see
     * process-flow.json allowedRoles across every process). Broadcasting to
     * every customer device is by far the highest-blast-radius operation in
     * this whole integration, so rather than inventing a parallel auth system
     * it is gated behind an explicit {@code isPlatformAdmin: true} flag on the
     * caller's own {@code stores} document — settable today only via direct
     * database access, which is intentional (nobody can self-grant it through
     * any API). Once a real ADMIN userType/login exists, replace this check
     * with {@code "admin".equalsIgnoreCase(callerUserType)} and drop the
     * {@code isPlatformAdmin} flag. See docs/FCM_INTEGRATION.md §Security.
     */
    private boolean callerIsPlatformAdmin(String callerUserType, String callerUserId, NktDynamicRepository repo) {
        if (!"business".equalsIgnoreCase(callerUserType)) return false;
        return repo.findOne("stores", "userId", callerUserId)
                .map(s -> Boolean.TRUE.equals(s.get("isPlatformAdmin")))
                .orElse(false);
    }

    private List<NotificationDispatchService.Recipient> activeEmployeeRecipients(String storeId,
                                                                                  NktDynamicRepository repo) {
        List<Map<String, Object>> employees = repo.findAll("store_staff_employees", Map.of("storeId", storeId));
        List<NotificationDispatchService.Recipient> recipients = new ArrayList<>();
        for (Map<String, Object> emp : employees) {
            String status = str(emp, "status");
            // Respects the existing REVOKE/RELEASE lifecycle: only a currently-Active
            // employee is notified. A revoked or released employee's status will not
            // equal "Active", so they are silently excluded — no separate check needed.
            if (status != null && status.equalsIgnoreCase("Active")) {
                recipients.add(new NotificationDispatchService.Recipient(str(emp, "employeeId"), "employee", storeId));
            }
        }
        return recipients;
    }

    private List<NotificationDispatchService.Recipient> ownerRecipient(String storeId, NktDynamicRepository repo) {
        return repo.findOne("stores", "storeId", storeId)
                .map(store -> List.of(new NotificationDispatchService.Recipient(str(store, "userId"), "business", storeId)))
                .orElse(List.of());
    }
}
