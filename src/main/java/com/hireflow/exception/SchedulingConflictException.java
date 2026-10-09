package com.hireflow.exception;

/**
 * Thrown when an interview schedule assignment violates non-overlapping constraints.
 */
public class SchedulingConflictException extends HireFlowException {
    public SchedulingConflictException(String message) {
        super(message);
    }
}
