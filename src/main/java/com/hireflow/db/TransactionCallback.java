package com.hireflow.db;

import java.sql.Connection;

/**
 * Functional interface for transactional operations that return a value.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T execute(Connection connection) throws Exception;
}
