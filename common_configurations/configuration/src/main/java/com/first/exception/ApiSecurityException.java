package com.first.exception;

/**
 * Thrown when a request fails JWT validation, role checks, or rate limiting.
 */
public class ApiSecurityException extends RuntimeException {

    public ApiSecurityException(String message) {
        super(message);
    }

    public ApiSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
