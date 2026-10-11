package com.hireflow.db;

import com.hireflow.exception.DatabaseException;
import com.hireflow.exception.TransactionRetryableException;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Random;

/**
 * Lightweight, production-grade transaction manager for HireFlow JDBC operations.
 * <p>
 * Key invariants:
 * 1. Acquires connections from {@link DataSource} and guarantees return to pool.
 * 2. Manages atomicity: autoCommit=false, commit on success, rollback on error.
 * 3. Thread-safe ambient context via ThreadLocal with strict cleanup.
 * 4. Preserves primary exceptions: rollback/close errors are appended as suppressed exceptions.
 * 5. Rejects nested transactions explicitly with {@link IllegalStateException}.
 * 6. Automated bounded retry for deadlock (40P01) and serialization (40001) anomalies using fresh connections.
 * 7. Non-retryable exceptions (unique/check/FK/domain violations) fail fast without retries.
 * 8. Wraps active transaction connection in close-suppressing proxy so repositories do not prematurely close it.
 * 9. Never leaks raw credentials through logs or exception messages.
 */
public class TransactionManager {

    private static final ThreadLocal<Connection> CURRENT_CONNECTION = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ROLLBACK_ONLY = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 50;

    private final DataSource dataSource;
    private final int maxRetries;
    private final Random random = new Random();

    public TransactionManager(DataSource dataSource) {
        this(dataSource, DEFAULT_MAX_RETRIES);
    }

    public TransactionManager(DataSource dataSource, int maxRetries) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
        this.maxRetries = Math.max(0, maxRetries);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public boolean isTransactionActive() {
        return CURRENT_CONNECTION.get() != null;
    }

    /**
     * Returns the active transaction connection if one exists on the current thread.
     * The returned connection is a close-suppressing proxy; closing it will not terminate
     * the transaction or close the underlying pooled connection prematurely.
     *
     * @return active transaction connection proxy, or null if no transaction is active
     */
    public Connection getActiveConnection() {
        return CURRENT_CONNECTION.get();
    }

    /**
     * Provides a connection suitable for repository operations.
     * If a transaction is active on the current thread, returns the transaction connection proxy.
     * If no transaction is active, returns a new standalone connection from the pool that the
     * caller must close.
     */
    public Connection getConnection() throws SQLException {
        Connection active = CURRENT_CONNECTION.get();
        if (active != null) {
            return active;
        }
        return dataSource.getConnection();
    }

    /**
     * Executes a transactional action returning a result.
     */
    public <T> T runInTransaction(TransactionCallback<T> action) {
        Objects.requireNonNull(action, "TransactionCallback cannot be null");

        // Requirement 5: Explicitly reject unsupported nested transactions
        if (isTransactionActive()) {
            throw new IllegalStateException(
                    "Nested transactions are not supported. An active transaction is already bound to thread '" +
                            Thread.currentThread().getName() + "'."
            );
        }

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return executeSingleTransaction(action);
            } catch (Throwable ex) {
                if (isRetryableFailure(ex) && attempts <= maxRetries) {
                    performBackoff(attempts);
                    continue; // Retry with a fresh connection from the pool
                }
                // Rethrow translated exception
                if (ex instanceof RuntimeException re) {
                    throw re;
                } else if (ex instanceof SQLException sqlEx) {
                    throw SQLExceptionTranslator.translate("Transaction failed", sqlEx);
                } else {
                    String sanitized = SQLExceptionTranslator.sanitizeMessage(ex.getMessage());
                    throw new DatabaseException("Transaction execution failed: " + sanitized, ex);
                }
            }
        }
    }

    /**
     * Executes a transactional action without a return value.
     */
    public void runInTransactionWithoutResult(TransactionRunnable action) {
        Objects.requireNonNull(action, "TransactionRunnable cannot be null");
        runInTransaction(conn -> {
            action.run(conn);
            return null;
        });
    }

    private <T> T executeSingleTransaction(TransactionCallback<T> action) throws Throwable {
        Connection physicalConn = null;
        Throwable primaryException = null;

        try {
            ROLLBACK_ONLY.set(Boolean.FALSE);
            physicalConn = dataSource.getConnection();
            physicalConn.setAutoCommit(false);

            // Requirement 8: Wrap in close-suppressing proxy
            Connection transactionalConn = createCloseSuppressingProxy(physicalConn);
            CURRENT_CONNECTION.set(transactionalConn);

            T result = action.execute(transactionalConn);

            if (isRollbackOnly()) {
                throw new DatabaseException(
                        "Transaction was marked as rollback-only due to an earlier failure inside the transaction."
                );
            }

            physicalConn.commit();
            return result;
        } catch (Throwable ex) {
            primaryException = ex;
            if (physicalConn != null) {
                try {
                    physicalConn.rollback();
                } catch (Throwable rbEx) {
                    // Requirement 4: Append rollback error as suppressed without masking primary exception
                    primaryException.addSuppressed(rbEx);
                }
            }
            throw primaryException;
        } finally {
            CURRENT_CONNECTION.remove();
            ROLLBACK_ONLY.remove();
            if (physicalConn != null) {
                try {
                    if (!physicalConn.isClosed()) {
                        physicalConn.setAutoCommit(true);
                        physicalConn.close(); // Safely return physical connection to pool
                    }
                } catch (Throwable clEx) {
                    if (primaryException != null) {
                        primaryException.addSuppressed(clEx);
                    } else {
                        String sanitized = SQLExceptionTranslator.sanitizeMessage(clEx.getMessage());
                        throw new DatabaseException("Failed to close database connection: " + sanitized, clEx);
                    }
                }
            }
        }
    }

    /**
     * Flags the ambient transaction as rollback-only so it cannot be committed.
     */
    public static void setRollbackOnly() {
        ROLLBACK_ONLY.set(Boolean.TRUE);
    }

    /**
     * Checks whether the active transaction on the current thread is marked as rollback-only.
     */
    public static boolean isRollbackOnly() {
        return Boolean.TRUE.equals(ROLLBACK_ONLY.get());
    }

    /**
     * Ambient static accessor for current thread's active transaction connection.
     */
    public static Connection getCurrentConnection() {
        return CURRENT_CONNECTION.get();
    }

    /**
     * Checks whether an active transaction is bound to the current thread.
     */
    public static boolean isCurrentTransactionActive() {
        return CURRENT_CONNECTION.get() != null;
    }

    /**
     * Creates a dynamic proxy for the active connection that intercepts close() calls
     * to prevent premature pool return by repository code, and strictly restricts
     * manual transaction demarcation (commit, rollback, setAutoCommit, setTransactionIsolation).
     */
    private Connection createCloseSuppressingProxy(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("close".equals(methodName)) {
                        // Suppress close call during active transaction: TransactionManager owns lifecycle
                        return null;
                    }
                    if ("commit".equals(methodName)) {
                        throw new UnsupportedOperationException(
                                "commit() cannot be invoked directly on an active transaction connection. " +
                                "Transaction demarcation is managed exclusively by TransactionManager."
                        );
                    }
                    if ("rollback".equals(methodName)) {
                        throw new UnsupportedOperationException(
                                "rollback() cannot be invoked directly on an active transaction connection. " +
                                "Transaction demarcation is managed exclusively by TransactionManager."
                        );
                    }
                    if ("setAutoCommit".equals(methodName)) {
                        throw new UnsupportedOperationException(
                                "setAutoCommit() cannot be invoked on an active transaction connection. " +
                                "Transaction demarcation is managed exclusively by TransactionManager."
                        );
                    }
                    if ("setTransactionIsolation".equals(methodName)) {
                        throw new UnsupportedOperationException(
                                "setTransactionIsolation() cannot be invoked on an active transaction connection. " +
                                "Transaction demarcation is managed exclusively by TransactionManager."
                        );
                    }
                    if ("isClosed".equals(methodName)) {
                        return target.isClosed();
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("unwrap".equals(methodName)) {
                        Class<?> iface = (Class<?>) args[0];
                        if (iface.isInstance(proxy) || iface.equals(Connection.class)) {
                            return proxy;
                        }
                        if (iface.isInstance(target)) {
                            return target;
                        }
                        return target.unwrap(iface);
                    }
                    if ("isWrapperFor".equals(methodName)) {
                        Class<?> iface = (Class<?>) args[0];
                        return iface.isInstance(proxy) || iface.equals(Connection.class)
                                || iface.isInstance(target) || target.isWrapperFor(iface);
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                }
        );
    }

    private boolean isRetryableFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TransactionRetryableException) {
                return true;
            }
            if (current instanceof SQLException sqlEx && SQLExceptionTranslator.isRetryable(sqlEx)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void performBackoff(int attempt) {
        long delay = INITIAL_BACKOFF_MS * (1L << (attempt - 1)) + random.nextInt(30);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("Transaction retry backoff interrupted", ie);
        }
    }
}
