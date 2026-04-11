package com.first.components;


import java.time.LocalDate;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.first.exception.ValidationException;
import com.first.services.ValidationService;

@Component
public class CustomValidationRules {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public void validate(String rule, Object value, String errorMsg) {
        switch (rule.toUpperCase()) {
            case "VALID_EMAIL":
                validateEmail(value, errorMsg);
                break;
            case "NO_SPECIAL_CHARS":
                validateNoSpecialChars(value, errorMsg);
                break;
            case "MUST_BE_TRUE":
                validateMustBeTrue(value, errorMsg);
                break;
            case "DATE_IN_PAST":
                validateDateInPast(value, errorMsg);
                break;
            case "PHONE_NUMBER":
                validatePhoneNumber(value, errorMsg);
                break;
            default:
                // Unknown rule - skip
                break;
        }
    }

    private void validateEmail(Object value, String errorMsg) {
        if (!EMAIL_PATTERN.matcher(value.toString()).matches()) {
            throw new ValidationException(errorMsg);
        }
    }

    private void validateNoSpecialChars(Object value, String errorMsg) {
        if (!value.toString().matches("^[a-zA-Z0-9_]+$")) {
            throw new ValidationException(errorMsg);
        }
    }

    private void validateMustBeTrue(Object value, String errorMsg) {
        if (!Boolean.parseBoolean(value.toString())) {
            throw new ValidationException(errorMsg);
        }
    }

    private void validateDateInPast(Object value, String errorMsg) {
        try {
            LocalDate date = LocalDate.parse(value.toString());
            if (date.isAfter(LocalDate.now())) {
                throw new ValidationException(errorMsg);
            }
        } catch (Exception e) {
            throw new ValidationException("Invalid date format");
        }
    }

    private void validatePhoneNumber(Object value, String errorMsg) {
        if (!value.toString().matches("^[0-9]{10}$")) {
            throw new ValidationException(errorMsg);
        }
    }
}