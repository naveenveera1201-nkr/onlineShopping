package com.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retries FCM notifications the recipient still hasn't opened.
 *
 * FCM has no built-in delivered/read receipt — only the client app knows
 * when a notification was actually opened, and it tells us via the
 * FCM_MARK_NOTIFICATION_READ process (see
 * {@link com.service.handlers.NktNotificationHandler#markNotificationRead()}).
 * This job periodically scans the {@code notifications} history collection
 * (through {@link NotificationDispatchService}) for rows still
 * {@code status=SENT} past the configured threshold, re-sends each one to
 * the recipient's CURRENTLY active devices, and gives up — marking the row
 * {@code EXPIRED} — once {@code notification.retry.max-attempts} is
 * reached, so a permanently uninstalled app or an inactive user is never
 * retried forever.
 *
 * All Mongo/Firebase access stays inside {@link NotificationDispatchService};
 * this class only decides WHEN to look and calls into that service — same
 * separation of concerns as every FCM_* handler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryScheduler {

    private final NotificationDispatchService dispatch;

    @Value("${notification.retry.threshold-minutes:15}")
    private long thresholdMinutes;

    @Scheduled(fixedDelayString = "${notification.retry.fixed-delay-ms:60000}")
    public void retryUnreadNotifications() {
		log.info("Notification retry job is triggered at :: {}", LocalDateTime.now());
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(thresholdMinutes);
		List<Map<String, Object>> candidates = dispatch.findUnreadOlderThan(cutoff);

		if (candidates.isEmpty()) {
			return;
		}

        log.info("Notification retry job: {} unread notification(s) past {} min threshold",
                candidates.size(), thresholdMinutes);

        for (Map<String, Object> notif : candidates) {
            Object idObj = notif.get("id");
            if (idObj == null) {
                continue;
            }
            try {
                dispatch.resendUnread(idObj.toString());
            } catch (Exception e) {
                log.warn("Failed to resend notification {} (non-fatal): {}", idObj, e.getMessage());
            }
        }
    }
}
