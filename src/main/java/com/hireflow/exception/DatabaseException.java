package com.hireflow.exception;

/**
 * Unchecked exception thrown when a database or persistence operation fails.
 */
public class DatabaseException extends HireFlowException {

    private final String sqlState;

    public DatabaseException(String message) {
        this(message, null, null);
    }

    public DatabaseException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public DatabaseException(String message, Throwable cause, String sqlState) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public String getSqlState() {
        return sqlState;
    }
}
