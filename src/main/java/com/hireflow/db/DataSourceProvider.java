package com.hireflow.db;

import com.hireflow.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of a HikariCP connection pool based on {@link DatabaseConfig}.
 * Provides thread-safe connection pooling, health verification, and clean shutdown.
 */
public class DataSourceProvider implements AutoCloseable {

    private final DatabaseConfig config;
    private final HikariDataSource dataSource;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DataSourceProvider(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "DatabaseConfig cannot be null");
        this.dataSource = initDataSource(config);
    }

    private static HikariDataSource initDataSource(DatabaseConfig cfg) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("HireFlow-Pool-" + cfg.getMode().name());
        hikari.setJdbcUrl(cfg.getJdbcUrl());
        hikari.setUsername(cfg.getUsername());
        hikari.setPassword(cfg.getPassword());
        hikari.setMaximumPoolSize(cfg.getMaximumPoolSize());
        hikari.setMinimumIdle(cfg.getMinimumIdle());
        hikari.setConnectionTimeout(cfg.getConnectionTimeoutMs());
        hikari.setIdleTimeout(cfg.getIdleTimeoutMs());
        hikari.setMaxLifetime(cfg.getMaxLifetimeMs());
        if (cfg.getLeakDetectionThresholdMs() > 0) {
            hikari.setLeakDetectionThreshold(cfg.getLeakDetectionThresholdMs());
        }

        // Recommended PostgreSQL driver performance properties
        hikari.addDataSourceProperty("reWriteBatchedInserts", "true");
        hikari.addDataSourceProperty("ApplicationName", "HireFlow");

        try {
            return new HikariDataSource(hikari);
        } catch (Exception ex) {
            String sanitizedErr = SQLExceptionTranslator.sanitizeMessage(ex.getMessage());
            if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
                sanitizedErr = sanitizedErr.replace(cfg.getPassword(), "******");
            }
            throw new DatabaseException("Failed to initialize HikariCP connection pool: " + sanitizedErr, ex);
        }
    }

    public DataSource getDataSource() {
        checkNotClosed();
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        checkNotClosed();
        return dataSource.getConnection();
    }

    /**
     * Verifies that the database is reachable and accepting connections.
     *
     * @param timeoutSeconds maximum seconds to wait for ping
     * @return true if database connection succeeded and is valid
     */
    public boolean verifyConnectionHealth(int timeoutSeconds) {
        if (isClosed()) return false;
        try (Connection conn = getConnection()) {
            return conn.isValid(Math.max(1, timeoutSeconds));
        } catch (Exception ex) {
            return false;
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public DatabaseMode getMode() {
        return config.getMode();
    }

    public boolean isClosed() {
        return closed.get() || dataSource.isClosed();
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("DataSourceProvider is closed and cannot dispense connections.");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}
