package com.hireflow.db;

/**
 * Runtime persistence modes for HireFlow.
 */
public enum DatabaseMode {
    IN_MEMORY,
    JDBC_DEV,
    JDBC_TEST;

    public static DatabaseMode fromString(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return IN_MEMORY;
        }
        try {
            return DatabaseMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IN_MEMORY;
        }
    }

    public boolean isInMemory() {
        return this == IN_MEMORY;
    }

    public boolean isJdbc() {
        return this == JDBC_DEV || this == JDBC_TEST;
    }

    public boolean isTest() {
        return this == JDBC_TEST;
    }

    public boolean isDev() {
        return this == JDBC_DEV;
    }
}
