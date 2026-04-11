package com.first.exception;

/**
 * Thrown when a request parameter fails validation rules
 * defined in the API YAML config (required, minLength, maxLength, pattern, etc.).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
