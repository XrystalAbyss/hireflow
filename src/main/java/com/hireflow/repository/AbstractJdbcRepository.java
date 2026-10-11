package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.SQLExceptionTranslator;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.ValidationException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base abstract class for JDBC-backed repositories in HireFlow.
 * Provides unified connection acquisition via TransactionManager ambient context
 * or standalone pooling, safe transaction participation for multi-table operations,
 * input validation, ID normalization, SQLSTATE translation, and destructive action safeguards.
 *
 * @param <T>  Domain entity type
 * @param <ID> Entity identifier type
 */
public abstract class AbstractJdbcRepository<T, ID> implements CrudRepository<T, ID> {

    protected final DataSource dataSource;
    protected final TransactionManager transactionManager;
    private final Function<T, ID> idExtractor;
    private final String entityName;

    protected AbstractJdbcRepository(DataSource dataSource, Function<T, ID> idExtractor, String entityName) {
        this(dataSource, new TransactionManager(dataSource), idExtractor, entityName);
    }

    protected AbstractJdbcRepository(DataSourceProvider provider, Function<T, ID> idExtractor, String entityName) {
        this(Objects.requireNonNull(provider, "DataSourceProvider cannot be null.").getDataSource(),
                new TransactionManager(provider.getDataSource()), idExtractor, entityName);
    }

    protected AbstractJdbcRepository(TransactionManager txManager, Function<T, ID> idExtractor, String entityName) {
        this(Objects.requireNonNull(txManager, "TransactionManager cannot be null.").getDataSource(),
                txManager, idExtractor, entityName);
    }

    protected AbstractJdbcRepository(DataSource dataSource, TransactionManager txManager,
                                     Function<T, ID> idExtractor, String entityName) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null.");
        this.transactionManager = Objects.requireNonNull(txManager, "TransactionManager cannot be null.");
        this.idExtractor = Objects.requireNonNull(idExtractor, "ID extractor must not be null.");
        this.entityName = Objects.requireNonNull(entityName, "Entity name must not be null.");
    }

    /**
     * Acquires the active transaction connection if within TransactionManager context,
     * otherwise borrows a connection from the underlying DataSource pool.
     */
    protected Connection getConnection() throws SQLException {
        Connection active = TransactionManager.getCurrentConnection();
        if (active != null) {
            return active;
        }
        return dataSource.getConnection();
    }

    /**
     * Executes an action participating in an existing ambient transaction if one is active,
     * or creating a new atomic transaction if none is active. Prevents illegal nested
     * transactions while guaranteeing atomicity for multi-table operations.
     */
    protected <R> R executeTransactional(TransactionalAction<R> action) {
        if (TransactionManager.isCurrentTransactionActive()) {
            try {
                Connection conn = getConnection();
                return action.execute(conn);
            } catch (SQLException ex) {
                throw translateException("Transactional operation", ex);
            }
        } else {
            return transactionManager.runInTransaction(conn -> {
                try {
                    return action.execute(conn);
                } catch (SQLException ex) {
                    throw translateException("Transactional operation", ex);
                }
            });
        }
    }

    protected void executeTransactionalWithoutResult(TransactionalRunnable action) {
        executeTransactional(conn -> {
            action.run(conn);
            return null;
        });
    }

    /**
     * Defensive safety check: clear() is restricted to test databases and requires an explicit test-mode setting.
     * Prevents accidental destructive deletion on development or production databases.
     */
    protected void ensureClearAllowed() {
        // 1. Require explicit test-mode authorization setting
        boolean isExplicitTestMode = Boolean.getBoolean("hireflow.allow.destructive.clear")
                || "JDBC_TEST".equalsIgnoreCase(System.getProperty("hireflow.storage.mode"))
                || "JDBC_TEST".equalsIgnoreCase(System.getenv("HIREFLOW_STORAGE_MODE"))
                || "test".equalsIgnoreCase(System.getProperty("hireflow.environment"))
                || "test".equalsIgnoreCase(System.getenv("HIREFLOW_ENVIRONMENT"));

        if (!isExplicitTestMode) {
            throw new UnsupportedOperationException(
                    "Destructive clear() operation is strictly prohibited against non-test databases: " +
                            "requires an explicit test-mode setting (e.g. -Dhireflow.storage.mode=JDBC_TEST or -Dhireflow.allow.destructive.clear=true)."
            );
        }

        // 2. Confirm the configured database target is strictly hireflow_test
        try (Connection conn = getConnection()) {
            String url = conn.getMetaData().getURL();
            String catalog = conn.getCatalog();

            boolean isDevDb = (url != null && url.toLowerCase().contains("hireflow_dev"))
                    || (catalog != null && catalog.equalsIgnoreCase("hireflow_dev"));
            if (isDevDb) {
                throw new UnsupportedOperationException(
                        "Destructive clear() operation is strictly prohibited against non-test databases: " +
                                "connected to development database 'hireflow_dev'."
                );
            }

            boolean isConfirmedTestDb = (url != null && url.toLowerCase().contains("hireflow_test"))
                    || (catalog != null && catalog.equalsIgnoreCase("hireflow_test"));
            if (!isConfirmedTestDb) {
                throw new UnsupportedOperationException(
                        "Destructive clear() operation is strictly prohibited against non-test databases. " +
                                "Connected target: " + (catalog != null ? catalog : url)
                );
            }
        } catch (SQLException ex) {
            throw translateException("Verify database safety for clear", ex);
        }
    }

    protected String normalizeId(String id) {
        return id != null ? id.trim().toUpperCase() : null;
    }

    protected void validateEntityForWrite(T entity, String operation) {
        if (entity == null) {
            throw new ValidationException(entityName + " to " + operation + " cannot be null.");
        }
        ID id = idExtractor.apply(entity);
        if (id == null || (id instanceof String s && s.trim().isEmpty())) {
            throw new ValidationException(entityName + " ID cannot be null.");
        }
    }

    protected RuntimeException translateException(String operation, SQLException ex) {
        if (TransactionManager.isCurrentTransactionActive()) {
            TransactionManager.setRollbackOnly();
        }
        return SQLExceptionTranslator.translate(operation + " " + entityName, ex);
    }

    protected String getEntityName() {
        return entityName;
    }

    protected ID extractId(T entity) {
        return idExtractor.apply(entity);
    }

    @FunctionalInterface
    protected interface TransactionalAction<R> {
        R execute(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    protected interface TransactionalRunnable {
        void run(Connection conn) throws SQLException;
    }
}
