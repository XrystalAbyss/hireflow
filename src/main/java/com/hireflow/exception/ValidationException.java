package com.hireflow.exception;

/**
 * Thrown when domain or form data fails validation rules (e.g., negative CGPA, bad email).
 */
public class ValidationException extends HireFlowException {
    public ValidationException(String message) {
        super(message);
    }
}
