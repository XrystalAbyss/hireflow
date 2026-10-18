package com.hireflow.api.exception;

import com.hireflow.exception.EntityNotFoundException;

/**
 * Thrown when an interview scheduling preview identifier is missing or unknown.
 * Maps to HTTP 404 (Not Found).
 */
public class PreviewNotFoundException extends EntityNotFoundException {
    public PreviewNotFoundException(String message) {
        super(message);
    }
}
