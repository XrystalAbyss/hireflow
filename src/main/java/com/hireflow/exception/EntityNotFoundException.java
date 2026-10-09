package com.hireflow.exception;

/**
 * Thrown when an expected entity (Student, Drive, Application, Interviewer) does not exist.
 */
public class EntityNotFoundException extends HireFlowException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
