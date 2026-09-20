package com.configs;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Admin SDK configuration.
 *
 * Credential resolution order (first match wins) — configured via
 * {@code application.yml} / environment variables, never hard-coded here:
 * <ol>
 *   <li>{@code firebase.credentials-json} (env {@code FIREBASE_CREDENTIALS_JSON}) —
 *       the full service-account JSON, base64-encoded. Recommended for Render
 *       or any host where mounting a file is inconvenient. The decoded JSON is
 *       kept in memory only — nothing is ever written to disk.</li>
 *   <li>{@code firebase.credentials-path} (env {@code FIREBASE_CREDENTIALS_PATH}) —
 *       absolute path to a service-account JSON file on disk. Typical for
 *       local development.</li>
 *   <li>{@code GOOGLE_APPLICATION_CREDENTIALS} / standard Application Default
 *       Credentials discovery — final fallback.</li>
 * </ol>
 *
 * When {@code firebase.enabled=false} (the default — see application.yml),
 * no {@link FirebaseApp} is created and the {@code firebaseMessaging} bean is
 * {@code null}; {@link com.service.FirebaseNotificationService} checks for
 * that and turns every send into a safe, logged no-op. This lets the rest of
 * the application boot normally on a machine with no Firebase credentials
 * configured (e.g. a fresh local checkout).
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Value("${firebase.credentials-json:}")
    private String credentialsJsonBase64;

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {

        if (!firebaseEnabled) {
            log.warn("Firebase is DISABLED (firebase.enabled=false). Push notifications will be no-ops.");
            return null;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = resolveCredentials();

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(credentials);

        if (projectId != null && !projectId.isBlank()) {
            optionsBuilder.setProjectId(projectId);
        }

        FirebaseApp app = FirebaseApp.initializeApp(optionsBuilder.build());
        log.info("FirebaseApp initialised successfully (projectId={})",
                projectId == null || projectId.isBlank() ? "<from-credentials>" : projectId);
        return app;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            return null; // firebase.enabled=false — FirebaseNotificationService checks for null
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    /**
     * Used only to verify Firebase Phone-Auth ID tokens (see
     * {@link com.service.FirebaseAuthService}) — the SMS OTP itself is sent
     * and checked entirely by the client SDK; the Admin SDK never sends SMS.
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            return null; // firebase.enabled=false — FirebaseAuthService checks for null
        }
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private GoogleCredentials resolveCredentials() throws IOException {

        // 1) Inline base64 JSON — best for Render / Heroku / any 12-factor host
        if (credentialsJsonBase64 != null && !credentialsJsonBase64.isBlank()) {
            log.info("Loading Firebase credentials from firebase.credentials-json (base64 env var)");
            byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64.trim());
            try (InputStream is = new ByteArrayInputStream(decoded)) {
                return GoogleCredentials.fromStream(is);
            }
        }

        // 2) Explicit file path — local development
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.info("Loading Firebase credentials from firebase.credentials-path={}", credentialsPath);
            try (InputStream is = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(is);
            }
        }

        // 3) Fallback: GOOGLE_APPLICATION_CREDENTIALS / ADC (metadata server, gcloud, etc.)
        log.info("Loading Firebase credentials via Application Default Credentials "
                + "(GOOGLE_APPLICATION_CREDENTIALS or environment ADC)");
        return GoogleCredentials.getApplicationDefault();
    }
}
