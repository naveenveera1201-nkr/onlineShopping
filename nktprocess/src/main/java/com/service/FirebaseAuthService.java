package com.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around the Firebase Admin SDK's ID-token verification for
 * Phone Authentication. This is the ONLY class that talks to
 * {@link FirebaseAuth} directly — mirrors the boundary
 * {@link FirebaseNotificationService} already keeps for FCM sends (see that
 * class's Javadoc): no Firebase Admin SDK calls anywhere else, no
 * auth/business rules in here.
 *
 * Firebase Phone Authentication sends and checks the SMS OTP entirely on
 * the CLIENT (Android/iOS/Web SDK) — there is no server-side "send OTP"
 * Admin API call. Once the user enters the correct code on-device, the
 * client SDK hands back a Firebase ID token; this service's only job is to
 * verify that token really came from Firebase and really carries the phone
 * number the client claims. Everything after that (register-vs-login,
 * issuing our own app JWT, store lookup, etc.) stays exactly as before in
 * {@link com.service.handlers.NktAuthHandler#verifyOtp()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthService {

    private final FirebaseAuth firebaseAuth; // null when firebase.enabled=false

    /**
     * Verifies a Firebase Phone-Auth ID token and returns the verified E.164
     * phone number it carries, or {@link Optional#empty()} if the token is
     * missing, invalid, expired, or was not obtained via phone sign-in (no
     * {@code phone_number} claim).
     */
    public Optional<String> verifyPhoneIdToken(String idToken) {
        if (firebaseAuth == null) {
            log.warn("Firebase is DISABLED (firebase.enabled=false) — cannot verify phone ID token");
            return Optional.empty();
        }
        if (idToken == null || idToken.isBlank()) {
            return Optional.empty();
        }
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            Object phone = decoded.getClaims().get("phone_number");
            if (phone == null) {
                log.warn("Firebase ID token has no phone_number claim (uid={})", decoded.getUid());
                return Optional.empty();
            }
            return Optional.of(phone.toString());
        } catch (FirebaseAuthException e) {
            log.warn("Firebase ID token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
