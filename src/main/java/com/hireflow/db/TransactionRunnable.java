package com.hireflow.db;

import java.sql.Connection;

/**
 * Functional interface for transactional operations that do not return a value.
 */
@FunctionalInterface
public interface TransactionRunnable {
    void run(Connection connection) throws Exception;
}
