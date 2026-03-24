package com.configs.validations;

import java.util.HashSet;
import java.util.Set;

import com.CommonUtils;
import com.exceptions.ConfigValidationException;
import com.models.Status;

public class StatusConfigValidator implements ConfigValidator<Status> {

    private final Set<Integer> seenCodes = new HashSet<>();

    // Optional: library-reserved codes (can be empty)
    private static final Set<Integer> RESERVED_CODES = Set.of();

    public StatusConfigValidator() {}

    @Override
    public void validate(Status status) throws ConfigValidationException {

        if (status == null) {
            throw new ConfigValidationException("Status config cannot be null");
        }

        validateCode(status);
        validateDescription(status);
    }

    private void validateCode(Status status) throws ConfigValidationException {

        Integer code = status.getCode();

        if (code == null) {
            throw new ConfigValidationException("Status Code must not be null");
        }

        if (!seenCodes.add(code)) {
            throw new ConfigValidationException(
                    "Duplicate Status Code detected: " + code);
        }

        if (RESERVED_CODES.contains(code)) {
            throw new ConfigValidationException(
                    "Status Code is reserved and cannot be used: " + code);
        }
    }

    private void validateDescription(Status status) throws ConfigValidationException {

        if (CommonUtils.isBlank(status.getDescription())) {
            throw new ConfigValidationException(
                    "Description must not be empty for status code: " + status.getCode());
        }
    }
}