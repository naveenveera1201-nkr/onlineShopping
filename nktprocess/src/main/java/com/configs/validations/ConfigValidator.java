package com.configs.validations;

import com.exceptions.ProcessFlowException;

public interface ConfigValidator<T> {

    /**
     * Validates a single config entity.
     *
     * @param config the config object to validate
     * @throws ProcessFlowException if validation fails
     */
    void validate(T config) throws ProcessFlowException;
}
