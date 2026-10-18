package com.hireflow.api.exception;

import com.hireflow.exception.HireFlowException;

/**
 * Thrown when an advisory interview scheduling preview has already been consumed/published.
 * Prevents publication token replay and double-submission side effects.
 * Maps to HTTP 409 (Conflict).
 */
public class PreviewConsumedException extends HireFlowException {
    public PreviewConsumedException(String message) {
        super(message);
    }
}
