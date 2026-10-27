package com.hireflow.api.exception;

import com.hireflow.exception.HireFlowException;

/**
 * Thrown when an advisory interview scheduling preview has exceeded its 15-minute validity window.
 * Maps to HTTP 410 (Gone).
 */
public class PreviewExpiredException extends HireFlowException {
    public PreviewExpiredException(String message) {
        super(message);
    }
}
