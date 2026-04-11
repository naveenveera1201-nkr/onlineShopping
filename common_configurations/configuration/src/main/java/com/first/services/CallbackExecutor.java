package com.first.services;

import com.first.dto.CallbackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Executes post-processing callbacks (webhook, email, SMS) defined in the
 * API YAML config. Failures are logged but never propagate to the caller —
 * a callback error must never fail the primary request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackExecutor {

    private final RestTemplate restTemplate;

    public void execute(CallbackConfig callback, Map<String, Object> data) {
        if (callback == null || callback.getType() == null) return;
        switch (callback.getType().toUpperCase()) {
            case "WEBHOOK" -> executeWebhook(callback, data);
            case "EMAIL"   -> sendEmail(callback, data);
            case "SMS"     -> sendSms(callback, data);
            default        -> log.warn("Unknown callback type: {}", callback.getType());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void executeWebhook(CallbackConfig callback, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);
            restTemplate.postForEntity(callback.getUrl(), entity, String.class);
            log.info("Webhook delivered: url={}", callback.getUrl());
        } catch (Exception e) {
            log.error("Webhook delivery failed: url={}, reason={}", callback.getUrl(), e.getMessage());
        }
    }

    private void sendEmail(CallbackConfig callback, Map<String, Object> data) {
        // TODO: integrate email provider (SendGrid / SES)
        log.info("Email callback queued: recipient={}", callback.getRecipient());
    }

    private void sendSms(CallbackConfig callback, Map<String, Object> data) {
        // TODO: integrate SMS provider (Twilio / AWS SNS)
        log.info("SMS callback queued: recipient={}", callback.getRecipient());
    }
}
