package com.first.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Central exception handler. Returns consistent JSON for every error type.
 * Format: { "status": "ERROR_CODE", "message": "...", "timestamp": "..." }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(ApiSecurityException.class)
    public ResponseEntity<String> handleSecurity(ApiSecurityException ex) {
        log.warn("Security error: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "SECURITY_ERROR", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage(), ex);
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<String> build(HttpStatus status, String code, String message) {
        String safe = message == null ? "" : message.replace("\"", "'");
        String body = String.format(
                "{\"status\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                code, safe, LocalDateTime.now());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
