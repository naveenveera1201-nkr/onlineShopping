package com.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.models.fcm.FcmBatchEntry;
import com.models.fcm.FcmSendResult;
import com.models.fcm.FcmTokenOutcome;
import com.repository.NktDynamicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reusable MongoDB ⇄ Firebase orchestration layer, shared by
 * {@link com.service.handlers.NktNotificationHandler} (the FCM_* processes)
 * and {@link OrderNotificationService} (order-lifecycle pushes).
 *
 * This is the ONLY place that:
 * <ul>
 *   <li>resolves {@code user_devices} documents for a userId / group,</li>
 *   <li>calls {@link FirebaseNotificationService},</li>
 *   <li>deactivates a device the moment Firebase reports its token invalid
 *       ({@code isActive=false}, {@code inactiveReason="FCM_TOKEN_INVALID"}) —
 *       so a bad token is never retried on a later notification,</li>
 *   <li>and, best-effort, writes a {@code notifications} history row.</li>
 * </ul>
 * It contains NO Firebase Admin SDK calls of its own (that stays inside
 * {@link FirebaseNotificationService}) and NO order/auth/store business
 * rules (those stay in the handler layer) — see task requirement #17.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private static final String DEVICES_COLLECTION = "user_devices";
    private static final String HISTORY_COLLECTION = "notifications";

    private final NktDynamicRepository repo;
    private final FirebaseNotificationService firebase;

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /** Notify every active device belonging to a single userId. */
    public FcmSendResult notifyUser(String userId, String userType, String storeId, String title, String body,
                                     String notificationType, Map<String, Object> extra, boolean recordHistory) {
        List<Map<String, Object>> devices = activeDevicesFor(userId);
        List<Recipient> recipients = recordHistory ? List.of(new Recipient(userId, userType, storeId)) : null;
        return sendToDevicesAndRecord(devices, title, body, notificationType, extra, recipients);
    }

    /** Notify every active device belonging to any of several userIds (a resolved group). */
    public FcmSendResult notifyUsers(List<Recipient> recipients, String title, String body,
                                      String notificationType, Map<String, Object> extra, boolean recordHistory) {
        if (recipients == null || recipients.isEmpty()) return FcmSendResult.empty();
        List<String> userIds = recipients.stream().map(Recipient::userId).distinct().toList();
        List<Map<String, Object>> devices = activeDevicesFor(userIds);
        return sendToDevicesAndRecord(devices, title, body, notificationType, extra,
                recordHistory ? recipients : null);
    }

    /** Notify every active device of a whole userType (e.g. all customers). No per-user history by default. */
    public FcmSendResult notifyUserType(String userType, String title, String body, String notificationType,
                                         Map<String, Object> extra, boolean recordHistory) {
        List<Map<String, Object>> devices = activeDevicesByUserType(userType);
        return sendToDevicesAndRecord(devices, title, body, notificationType, extra,
                recordHistory ? devices.stream()
                        .map(d -> new Recipient(str(d, "userId"), str(d, "userType"), str(d, "storeId")))
                        .distinct().toList() : null);
    }

    /** Different message per target — FCM_SEND_BATCH_NOTIFICATION / per-employee assignment alerts. */
    public FcmSendResult notifyBatch(List<BatchTarget> targets, boolean recordHistory) {
        FcmSendResult result = FcmSendResult.empty();
        if (targets == null || targets.isEmpty()) return result;

        List<FcmBatchEntry> entries = new ArrayList<>();

        for (BatchTarget t : targets) {
            for (Map<String, Object> device : activeDevicesFor(t.userId())) {
                String token = str(device, "fcmToken");
                if (token == null || token.isBlank()) continue;
                entries.add(FcmBatchEntry.builder()
                        .token(token)
                        .title(t.title())
                        .body(t.body())
                        .data(dataPayload(t.notificationType(), t.extra()))
                        .build());
            }
        }

        result.setTotalDevices(entries.size());
        List<FcmTokenOutcome> outcomes = firebase.sendBatchMessages(entries);

        for (FcmTokenOutcome o : outcomes) {
            if (o.isSuccess()) {
                result.setSuccess(result.getSuccess() + 1);
            } else {
                result.setFailed(result.getFailed() + 1);
                if (o.isInvalidToken()) {
                    deactivateToken(o.getToken());
                    result.getInvalidTokens().add(o.getToken());
                }
            }
        }

        if (recordHistory) {
            for (BatchTarget t : targets) {
                recordHistory(t.userId(), null, t.notificationType(), t.title(), t.body(), t.extra());
            }
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Device resolution (also used directly by handlers that need to
    // pre-filter/authorise before deciding whether to send at all)
    // ─────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> activeDevicesFor(String userId) {
        if (userId == null) return List.of();
        return repo.findAll(DEVICES_COLLECTION, Map.of("userId", userId, "isActive", true));
    }

    public List<Map<String, Object>> activeDevicesFor(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return repo.findAll(DEVICES_COLLECTION, Map.of("userId", Map.of("$in", userIds), "isActive", true));
    }

    public List<Map<String, Object>> activeDevicesForStore(String storeId, String userType) {
        return repo.findAll(DEVICES_COLLECTION, Map.of("storeId", storeId, "userType", userType, "isActive", true));
    }

    public List<Map<String, Object>> activeDevicesByUserType(String userType) {
        return repo.findAll(DEVICES_COLLECTION, Map.of("userType", userType, "isActive", true));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Send + bookkeeping
    // ─────────────────────────────────────────────────────────────────────

    private FcmSendResult sendToDevicesAndRecord(List<Map<String, Object>> devices, String title, String body,
                                                  String notificationType, Map<String, Object> extra,
                                                  List<Recipient> recipientsForHistory) {
        if (devices == null || devices.isEmpty()) return FcmSendResult.empty();

        // De-duplicate tokens — a user with the same token registered twice
        // (e.g. a retried registration) must only receive one push.
        List<String> tokens = devices.stream()
                .map(d -> str(d, "fcmToken"))
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();

        Map<String, String> data = dataPayload(notificationType, extra);
        FcmSendResult result = firebase.sendToMultipleDevices(tokens, title, body, data);

        for (String invalid : result.getInvalidTokens()) {
            deactivateToken(invalid);
        }

        if (recipientsForHistory != null) {
            for (Recipient r : recipientsForHistory) {
                recordHistory(r.userId(), r.storeId(), notificationType, title, body, extra);
            }
        }
        return result;
    }

    private void deactivateToken(String fcmToken) {
        repo.updateFirst(DEVICES_COLLECTION,
                Map.of("fcmToken", fcmToken, "isActive", true),
                Map.of("isActive", false,
                        "inactiveReason", "FCM_TOKEN_INVALID",
                        "updatedAt", LocalDateTime.now().toString()));
        log.info("Deactivated invalid FCM token {}", FirebaseNotificationService.mask(fcmToken));
    }

    private Map<String, String> dataPayload(String notificationType, Map<String, Object> extra) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", notificationType == null ? "GENERAL" : notificationType);
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (v != null) data.put(k, v.toString());
            });
        }
        return data;
    }

    /** Best-effort notification history write — never allowed to break a send. */
    private void recordHistory(String userId, String storeId, String notificationType, String title,
                                String body, Map<String, Object> data) {
        try {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("userId", userId);
            rec.put("storeId", storeId);
            rec.put("notificationType", notificationType == null ? "GENERAL" : notificationType);
            rec.put("title", title);
            rec.put("body", body);
            rec.put("data", data == null ? Map.of() : data);
            rec.put("status", "SENT");
            rec.put("sentAt", LocalDateTime.now().toString());
            rec.put("readAt", null);
            rec.put("createdAt", LocalDateTime.now().toString());
            repo.insert(HISTORY_COLLECTION, rec);
        } catch (Exception e) {
            log.warn("Failed to record notification history (non-fatal): {}", e.getMessage());
        }
    }

    private String str(Map<String, Object> d, String k) {
        if (d == null) return null;
        Object v = d.get(k);
        return v == null ? null : v.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Small value types
    // ─────────────────────────────────────────────────────────────────────

    public record Recipient(String userId, String userType, String storeId) {}

    public record BatchTarget(String userId, String title, String body, String notificationType,
                               Map<String, Object> extra) {}
}
