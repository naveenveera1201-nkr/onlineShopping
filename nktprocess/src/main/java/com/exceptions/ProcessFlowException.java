package com.exceptions;

public class ProcessFlowException extends Exception {
    public ProcessFlowException(String message) {
        super(message);
    }

    public ProcessFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
