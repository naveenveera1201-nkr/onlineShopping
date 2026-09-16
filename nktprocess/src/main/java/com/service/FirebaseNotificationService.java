package com.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.models.fcm.FcmBatchEntry;
import com.models.fcm.FcmSendResult;
import com.models.fcm.FcmTokenOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around the Firebase Admin SDK. Responsibilities ONLY:
 * <ul>
 *   <li>build {@link Message} / {@link MulticastMessage} objects</li>
 *   <li>call {@link FirebaseMessaging}, using the current recommended
 *       {@code sendEachForMulticast} / {@code sendEach} APIs (NOT the
 *       deprecated {@code sendMulticast} / {@code sendAll})</li>
 *   <li>split requests into Firebase's supported batch size (500)</li>
 *   <li>translate Firebase exceptions into a clean {@link FcmTokenOutcome}
 *       per token, distinguishing a dead token from a transient/infra error</li>
 * </ul>
 * This class knows NOTHING about MongoDB, {@code user_devices}, orders, or
 * any other business concept — that orchestration lives in
 * {@link NotificationDispatchService}. Keeping the split this way is what
 * lets {@link com.service.handlers.NktOrderHandler} and
 * {@link com.service.handlers.NktNotificationHandler} share the same send
 * path without either one poking at the Firebase SDK directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseNotificationService {

    /** Firebase Admin SDK hard limit for a single multicast/batch send call. */
    public static final int MAX_BATCH_SIZE = 500;

    /** Null when firebase.enabled=false (see FirebaseConfig) — every method becomes a safe no-op. */
    private final FirebaseMessaging firebaseMessaging;

    // ─────────────────────────────────────────────────────────────────────
    // Single device
    // ─────────────────────────────────────────────────────────────────────

    /** Send a notification + data payload to exactly one device. */
    public FcmTokenOutcome sendToDevice(String token, String title, String body, Map<String, String> data) {
        if (!isEnabled()) return disabledOutcome(token);

        Message message = buildMessage(token, title, body, data);
        try {
            String messageId = firebaseMessaging.send(message);
            log.debug("FCM send ok token={} messageId={}", mask(token), messageId);
            return new FcmTokenOutcome(token, true, false, null);
        } catch (FirebaseMessagingException e) {
            return outcomeFromException(token, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multiple devices, SAME title/body/data (multicast)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Send the same notification to many devices, automatically split into
     * batches of {@value #MAX_BATCH_SIZE} (the Firebase-supported maximum).
     * Never sends thousands of devices in a single request.
     */
    public FcmSendResult sendToMultipleDevices(List<String> tokens, String title, String body,
                                                Map<String, String> data) {
        FcmSendResult result = FcmSendResult.empty();
        if (!isEnabled() || tokens == null || tokens.isEmpty()) return result;

        for (List<String> batch : partition(tokens, MAX_BATCH_SIZE)) {
            result.merge(sendMulticastBatch(batch, title, body, data));
        }
        return result;
    }

    private FcmSendResult sendMulticastBatch(List<String> tokens, String title, String body,
                                              Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .setAndroidConfig(androidConfig())
                .setApnsConfig(apnsConfig())
                .build();

        FcmSendResult result = FcmSendResult.builder().totalDevices(tokens.size()).build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse r = responses.get(i);
                String token = tokens.get(i);
                if (r.isSuccessful()) {
                    result.setSuccess(result.getSuccess() + 1);
                } else {
                    result.setFailed(result.getFailed() + 1);
                    if (isInvalidToken(r.getException())) {
                        result.getInvalidTokens().add(token);
                    }
                    log.warn("FCM send failed token={} reason={}", mask(token),
                            r.getException() != null ? r.getException().getMessagingErrorCode() : "UNKNOWN");
                }
            }
        } catch (FirebaseMessagingException e) {
            // Whole-batch failure (auth/config/quota problem) — every token failed,
            // but none of them are proven INVALID, so do not deactivate any device.
            log.error("FCM multicast batch failed entirely: {}", e.getMessage());
            result.setFailed(result.getFailed() + tokens.size());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Multiple devices, DIFFERENT title/body/data per token (true batch)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Send a distinct message per token — e.g. "Order ORD1001 assigned to
     * you" for EMP001 and "Order ORD1002 assigned to you" for EMP002 — using
     * the Firebase batch API rather than one common multicast message.
     * Returns one {@link FcmTokenOutcome} per entry, in the same order as
     * the input list, so the caller can correlate results back to
     * userIds/devices.
     */
    public List<FcmTokenOutcome> sendBatchMessages(List<FcmBatchEntry> entries) {
        List<FcmTokenOutcome> outcomes = new ArrayList<>();
        if (!isEnabled() || entries == null || entries.isEmpty()) return outcomes;

        for (List<FcmBatchEntry> batch : partition(entries, MAX_BATCH_SIZE)) {
            outcomes.addAll(sendBatch(batch));
        }
        return outcomes;
    }

    private List<FcmTokenOutcome> sendBatch(List<FcmBatchEntry> batch) {
        List<Message> messages = new ArrayList<>();
        for (FcmBatchEntry entry : batch) {
            messages.add(buildMessage(entry.getToken(), entry.getTitle(), entry.getBody(), entry.getData()));
        }

        List<FcmTokenOutcome> outcomes = new ArrayList<>();
        try {
            BatchResponse response = firebaseMessaging.sendEach(messages);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse r = responses.get(i);
                String token = batch.get(i).getToken();
                if (r.isSuccessful()) {
                    outcomes.add(new FcmTokenOutcome(token, true, false, null));
                } else {
                    boolean invalid = isInvalidToken(r.getException());
                    outcomes.add(new FcmTokenOutcome(token, false, invalid,
                            r.getException() != null ? r.getException().getMessage() : "unknown error"));
                    log.warn("FCM batch send failed token={} invalid={}", mask(token), invalid);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM batch send failed entirely: {}", e.getMessage());
            for (FcmBatchEntry entry : batch) {
                outcomes.add(new FcmTokenOutcome(entry.getToken(), false, false, "batch send failed"));
            }
        }
        return outcomes;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Topic (optional convenience — kept for future STORE_* broadcast use
    // once device volume makes per-device multicast impractical)
    // ─────────────────────────────────────────────────────────────────────

    public FcmTokenOutcome sendToTopic(String topic, String title, String body, Map<String, String> data) {
        if (!isEnabled()) return disabledOutcome(topic);
        Message message = Message.builder()
                .setTopic(topic)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .build();
        try {
            String id = firebaseMessaging.send(message);
            log.debug("FCM topic send ok topic={} messageId={}", topic, id);
            return new FcmTokenOutcome(topic, true, false, null);
        } catch (FirebaseMessagingException e) {
            return outcomeFromException(topic, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private boolean isEnabled() {
        return firebaseMessaging != null;
    }

    private FcmTokenOutcome disabledOutcome(String token) {
        log.warn("Firebase is disabled (firebase.enabled=false) — skipping send to {}", mask(token));
        return new FcmTokenOutcome(token, false, false, "FIREBASE_DISABLED");
    }

    private Message buildMessage(String token, String title, String body, Map<String, String> data) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .setAndroidConfig(androidConfig())
                .setApnsConfig(apnsConfig())
                .build();
    }

    private AndroidConfig androidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();
    }

    private ApnsConfig apnsConfig() {
        return ApnsConfig.builder()
                .setAps(Aps.builder().setContentAvailable(true).build())
                .build();
    }

    private FcmTokenOutcome outcomeFromException(String token, FirebaseMessagingException e) {
        boolean invalid = isInvalidToken(e);
        log.warn("FCM send failed token={} code={} invalid={}", mask(token), e.getMessagingErrorCode(), invalid);
        return new FcmTokenOutcome(token, false, invalid, e.getMessage());
    }

    /**
     * True only when Firebase says the token itself is dead — UNREGISTERED
     * (app uninstalled / token rotated) or INVALID_ARGUMENT (malformed
     * token). Any other error (UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED,
     * SENDER_ID_MISMATCH, ...) is treated as transient/infra and must NOT
     * deactivate the device.
     */
    private boolean isInvalidToken(FirebaseMessagingException e) {
        if (e == null) return false;
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }

    /** Mask a token for logs — never write a full FCM token to any log line. */
    public static String mask(String token) {
        if (token == null || token.length() < 10) return "****";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
