package com.hireflow.exception;

/**
 * Thrown when attempting to persist an entity with a duplicate unique key (e.g. ID, email, or application).
 */
public class DuplicateEntityException extends HireFlowException {
    public DuplicateEntityException(String message) {
        super(message);
    }
}
