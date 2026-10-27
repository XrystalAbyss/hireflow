package com.hireflow.db;

import com.hireflow.exception.*;

import java.sql.SQLException;

/**
 * Translates low-level JDBC {@link SQLException} instances into meaningful HireFlow
 * domain exceptions based on PostgreSQL standard SQLSTATE codes.
 */
public final class SQLExceptionTranslator {

    private SQLExceptionTranslator() {
    }

    /**
     * Translates a SQLException into the corresponding HireFlow domain exception.
     *
     * @param context description of the failing persistence operation
     * @param ex      the caught SQLException
     * @return a specific HireFlow unchecked domain exception
     */
    public static RuntimeException translate(String context, SQLException ex) {
        if (ex == null) {
            return new DatabaseException((context != null ? context + ": " : "") + "Unknown database error");
        }

        String sqlState = ex.getSQLState() != null ? ex.getSQLState().trim() : "";
        String rawMessage = ex.getMessage() != null ? ex.getMessage() : "";
        String sanitizedMsg = sanitizeMessage(rawMessage);
        String message = (context != null ? context + ": " : "") + sanitizedMsg;

        return switch (sqlState) {
            case "23505" -> // unique_violation
                    new DuplicateEntityException(message);
            case "23503" -> // foreign_key_violation
                    new ValidationException(message);
            case "23514" -> // check_violation
                    new ValidationException(message);
            case "23P01" -> // exclusion_violation
                    new SchedulingConflictException(message);
            case "40001", "40P01" -> // serialization_failure, deadlock_detected
                    new TransactionRetryableException(message, ex, sqlState);
            default -> new DatabaseException(message, ex, sqlState);
        };
    }

    /**
     * Determines whether the given SQLException represents a retryable concurrency
     * failure (e.g. deadlock 40P01 or serialization failure 40001).
     */
    public static boolean isRetryable(SQLException ex) {
        if (ex == null || ex.getSQLState() == null) return false;
        String state = ex.getSQLState().trim();
        return "40001".equals(state) || "40P01".equals(state);
    }

    /**
     * Strips credentials or sensitive connection fragments from exception messages.
     */
    public static String sanitizeMessage(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)(password|pwd|secret)=[^;\\s&]+", "$1=******")
                .replaceAll("://([^:]+):([^@]+)@", "://$1:******@");
    }
}
