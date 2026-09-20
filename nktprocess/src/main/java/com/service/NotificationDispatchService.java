package com.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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

    /** Give up resending (mark EXPIRED) once a notification has been retried this many times. */
    @Value("${notification.retry.max-attempts:3}")
    private int maxRetryAttempts;

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

        // Single-recipient sends (by far the common case — FCM_SEND_NOTIFICATION,
        // every OrderNotificationService call) get their history row written
        // BEFORE the push goes out, so the row's own id can ride inside the FCM
        // data payload as "notificationId". That is the only way the RECEIVING
        // device — not the caller of this API — ever learns the id, since it
        // never sees this method's return value. The client reads
        // remoteMessage.getData().get("notificationId") and echoes it back on
        // FCM_MARK_NOTIFICATION_READ when the user opens the notification.
        //
        // A multi-recipient group/batch send still gets one history row per
        // recipient, just written after the (single, shared) Firebase call as
        // before — a single shared payload can't carry a different id per
        // recipient without splitting into one Firebase call per recipient.
        String singleNotificationId = null;
        if (recipientsForHistory != null && recipientsForHistory.size() != 0) {
            Recipient only = recipientsForHistory.get(0);
            singleNotificationId = recordHistory(only.userId(), only.storeId(), notificationType, title, body, extra);
        }

        Map<String, String> data = dataPayload(notificationType, extra);
        if (singleNotificationId != null) {
            data.put("notificationId", singleNotificationId);
        }
        FcmSendResult result = firebase.sendToMultipleDevices(tokens, title, body, data);

        for (String invalid : result.getInvalidTokens()) {
            deactivateToken(invalid);
        }

        if (recipientsForHistory != null && recipientsForHistory.size() != 1) {
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

    /**
     * Best-effort notification history write — never allowed to break a send.
     * Returns the new history row's id (for callers that need it) or null if
     * the write itself failed.
     */
    private String recordHistory(String userId, String storeId, String notificationType, String title,
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
            rec.put("retryCount", 0);
            rec.put("lastRetryAt", null);
            rec.put("createdAt", LocalDateTime.now().toString());
            Map<String, Object> inserted = repo.insert(HISTORY_COLLECTION, rec);
            return inserted == null ? null : str(inserted, "id");
        } catch (Exception e) {
            log.warn("Failed to record notification history (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Unread-notification retry support (FCM has no native read receipt —
    // the client tells us via FCM_MARK_NOTIFICATION_READ; this scheduler-
    // facing API lets NotificationRetryScheduler find and resend the rest)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Marks a notification as read/opened by its recipient. Idempotent — a
     * second call on an already-read notification is a harmless no-op.
     * Ownership-checked: only the userId it was sent to may mark it read.
     */
    public boolean markAsRead(String notificationId, String userId) {
        if (notificationId == null || notificationId.isBlank()) return false;
        Map<String, Object> notif = repo.findById(HISTORY_COLLECTION, notificationId).orElse(null);
        if (notif == null) return false;
        if (userId != null && !userId.equals(str(notif, "userId"))) return false;
        if ("READ".equals(str(notif, "status"))) return true;

        repo.updateById(HISTORY_COLLECTION, notificationId, Map.of(
                "status", "READ",
                "readAt", LocalDateTime.now().toString()));
        return true;
    }

    /**
     * Notifications still {@code status=SENT} (not yet marked read) whose
     * {@code sentAt} is older than {@code cutoff} and that have not yet
     * exhausted {@code maxRetryAttempts} resends. Used by
     * {@code NotificationRetryScheduler}.
     */
    public List<Map<String, Object>> findUnreadOlderThan(LocalDateTime cutoff) {
        Map<String, Object> criteria = Map.of(
                "status", "SENT",
                "sentAt", Map.of("$lt", cutoff.toString()),
                "retryCount", Map.of("$lt", maxRetryAttempts));
        return repo.findAll(HISTORY_COLLECTION, criteria);
    }

    /**
     * Re-sends one notification history row to the recipient's CURRENT
     * active devices — never the token captured at original send time,
     * since that device may since have been deactivated or replaced. Does
     * not write a new history row; it updates the retry bookkeeping on the
     * existing one and marks it {@code EXPIRED} once {@code maxRetryAttempts}
     * is reached, so it is never picked up again.
     */
    public void resendUnread(String notificationId) {
        Map<String, Object> notif = repo.findById(HISTORY_COLLECTION, notificationId).orElse(null);
        if (notif == null) return;

        String userId = str(notif, "userId");
        List<Map<String, Object>> devices = activeDevicesFor(userId);
        int retryCount = notif.get("retryCount") instanceof Number n ? n.intValue() : 0;

        if (devices.isEmpty()) {
            repo.updateById(HISTORY_COLLECTION, notificationId, Map.of(
                    "status", "EXPIRED",
                    "lastRetryAt", LocalDateTime.now().toString()));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) notif.get("data");
        sendToDevicesAndRecord(devices, str(notif, "title"), str(notif, "body"),
                str(notif, "notificationType"), data, null);

        int newRetryCount = retryCount + 1;
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("retryCount", newRetryCount);
        update.put("lastRetryAt", LocalDateTime.now().toString());
        if (newRetryCount >= maxRetryAttempts) {
            update.put("status", "EXPIRED");
        }
        repo.updateById(HISTORY_COLLECTION, notificationId, update);
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
