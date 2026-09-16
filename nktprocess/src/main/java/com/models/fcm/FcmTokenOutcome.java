package com.models.fcm;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Per-token result of a single Firebase send, already translated out of raw
 * {@code FirebaseMessagingException} types so callers outside
 * {@code com.service.FirebaseNotificationService} never need to depend on
 * the Firebase Admin SDK directly.
 */
@Data
@AllArgsConstructor
public class FcmTokenOutcome {

    private String token;

    private boolean success;

    /** True only when Firebase says the token itself is dead (UNREGISTERED / INVALID_ARGUMENT). */
    private boolean invalidToken;

    /** Sanitised message — never the raw stack trace. Null on success. */
    private String errorMessage;
}
