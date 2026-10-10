package com.hireflow.exception;

/**
 * Exception thrown when a transaction fails due to a retryable concurrency anomaly
 * such as a deadlock (SQLSTATE 40P01) or serialization failure (SQLSTATE 40001).
 */
public class TransactionRetryableException extends DatabaseException {

    public TransactionRetryableException(String message, Throwable cause, String sqlState) {
        super(message, cause, sqlState);
    }
}
