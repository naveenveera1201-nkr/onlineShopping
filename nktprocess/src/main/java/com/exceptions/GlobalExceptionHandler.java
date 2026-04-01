package com.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the NKT no-code platform.
 *
 * Because the single entry-point (WorkflowEngineController) always returns
 * a plain {@code String}, all error responses are also plain JSON strings
 * rather than typed wrapper objects.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Resource not found ────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("NOT_FOUND", ex.getMessage()));
    }

    // ── Validation errors ─────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = (err instanceof org.springframework.validation.FieldError fe)
                    ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        String body = "{\"status\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\",\"errors\":"
                + mapToJson(errors) + "}";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // ── Business rule / runtime errors ────────────────────────────────────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("ERROR", ex.getMessage()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String json(String status, String message) {
        String safe = message == null ? "" : message.replace("\"", "'");
        return "{\"status\":\"" + status + "\",\"message\":\"" + safe + "\"}";
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":\"")
                .append(v == null ? "" : v.replace("\"", "'")).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') sb.setCharAt(sb.length() - 1, '}');
        else sb.append('}');
        return sb.toString();
    }
}
