package com.hireflow.exception;

/**
 * Base unchecked exception for all HireFlow domain and business errors.
 */
public class HireFlowException extends RuntimeException {
    public HireFlowException(String message) {
        super(message);
    }

    public HireFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
