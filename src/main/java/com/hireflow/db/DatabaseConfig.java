package com.hireflow.db;

import java.io.File;
import java.io.FileInputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Immutable configuration holder for database connectivity and HikariCP connection pooling.
 * Safely resolves configuration with precedence:
 * 1. Environment variables
 * 2. JVM System Properties
 * 3. Local gitignored 'hireflow-local.properties' (or custom file via HIREFLOW_CONFIG_FILE / hireflow.config.file)
 * 4. Safe production defaults
 */
public final class DatabaseConfig {

    private final DatabaseMode mode;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;
    private final long leakDetectionThresholdMs;

    private DatabaseConfig(Builder builder) {
        this.mode = builder.mode;
        this.jdbcUrl = builder.jdbcUrl;
        this.username = builder.username;
        this.password = builder.password != null ? builder.password : "";
        this.maximumPoolSize = builder.maximumPoolSize;
        this.minimumIdle = builder.minimumIdle;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.idleTimeoutMs = builder.idleTimeoutMs;
        this.maxLifetimeMs = builder.maxLifetimeMs;
        this.leakDetectionThresholdMs = builder.leakDetectionThresholdMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads database configuration resolving environment variables, system properties,
     * and local configuration files.
     */
    public static DatabaseConfig load() {
        Properties fileProps = new Properties();

        // 1. Resolve custom config file or default 'hireflow-local.properties'
        String customPath = resolveValue("HIREFLOW_CONFIG_FILE", "hireflow.config.file", "hireflow-local.properties");
        File localFile = new File(customPath);
        if (localFile.exists() && localFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                fileProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        // 2. Resolve properties with strict precedence
        String rawMode = resolveValue("HIREFLOW_STORAGE_MODE", "hireflow.storage.mode",
                fileProps.getProperty("hireflow.storage.mode", "IN_MEMORY"));
        DatabaseMode mode = DatabaseMode.fromString(rawMode);

        String defaultUrl = mode == DatabaseMode.JDBC_TEST
                ? "jdbc:postgresql://localhost:5432/hireflow_test"
                : "jdbc:postgresql://localhost:5432/hireflow_dev";

        String url = resolveValue("HIREFLOW_DB_URL", "hireflow.db.url",
                fileProps.getProperty("hireflow.db.url", defaultUrl));

        String user = resolveValue("HIREFLOW_DB_USER", "hireflow.db.user",
                fileProps.getProperty("hireflow.db.user", "hireflow_user"));

        String pass = resolveValue("HIREFLOW_DB_PASSWORD", "hireflow.db.password",
                fileProps.getProperty("hireflow.db.password", ""));

        int maxPool = parseInt(resolveValue("HIREFLOW_DB_POOL_MAX", "hireflow.db.pool.maximum-size",
                fileProps.getProperty("hireflow.db.pool.maximum-size", "10")), 10);

        int minIdle = parseInt(resolveValue("HIREFLOW_DB_POOL_MIN", "hireflow.db.pool.minimum-idle",
                fileProps.getProperty("hireflow.db.pool.minimum-idle", "2")), 2);

        long connTimeout = parseLong(resolveValue("HIREFLOW_DB_CONN_TIMEOUT", "hireflow.db.pool.connection-timeout-ms",
                fileProps.getProperty("hireflow.db.pool.connection-timeout-ms", "20000")), 20000L);

        long idleTimeout = parseLong(resolveValue("HIREFLOW_DB_IDLE_TIMEOUT", "hireflow.db.pool.idle-timeout-ms",
                fileProps.getProperty("hireflow.db.pool.idle-timeout-ms", "30000")), 30000L);

        long maxLifetime = parseLong(resolveValue("HIREFLOW_DB_MAX_LIFETIME", "hireflow.db.pool.max-lifetime-ms",
                fileProps.getProperty("hireflow.db.pool.max-lifetime-ms", "600000")), 600000L);

        long leakDetection = parseLong(resolveValue("HIREFLOW_DB_LEAK_DETECTION", "hireflow.db.pool.leak-detection-threshold-ms",
                fileProps.getProperty("hireflow.db.pool.leak-detection-threshold-ms", "10000")), 10000L);

        return builder()
                .mode(mode)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(maxPool)
                .minimumIdle(minIdle)
                .connectionTimeoutMs(connTimeout)
                .idleTimeoutMs(idleTimeout)
                .maxLifetimeMs(maxLifetime)
                .leakDetectionThresholdMs(leakDetection)
                .build();
    }

    private static String resolveValue(String envKey, String sysPropKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal.trim();
        }
        String sysVal = System.getProperty(sysPropKey);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            return sysVal.trim();
        }
        return defaultValue;
    }

    private static int parseInt(String val, int defaultVal) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception ex) {
            return defaultVal;
        }
    }

    private static long parseLong(String val, long defaultVal) {
        try {
            return Long.parseLong(val.trim());
        } catch (Exception ex) {
            return defaultVal;
        }
    }

    public DatabaseMode getMode() {
        return mode;
    }

    public boolean isInMemory() {
        return mode != null && mode.isInMemory();
    }

    public boolean isJdbc() {
        return mode != null && mode.isJdbc();
    }

    public boolean isTest() {
        return mode != null && mode.isTest();
    }

    public boolean isDev() {
        return mode != null && mode.isDev();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    public long getLeakDetectionThresholdMs() {
        return leakDetectionThresholdMs;
    }

    /**
     * Sanitized string representation guaranteeing passwords are masked.
     */
    public String toSafeString() {
        String safeUrl = jdbcUrl != null
                ? jdbcUrl.replaceAll("://([^:]+):([^@]+)@", "://$1:***@")
                : "";
        return "DatabaseConfig{" +
                "mode=" + mode +
                ", jdbcUrl='" + safeUrl + '\'' +
                ", username='" + username + '\'' +
                ", password='***'" +
                ", maximumPoolSize=" + maximumPoolSize +
                ", minimumIdle=" + minimumIdle +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", idleTimeoutMs=" + idleTimeoutMs +
                ", maxLifetimeMs=" + maxLifetimeMs +
                ", leakDetectionThresholdMs=" + leakDetectionThresholdMs +
                '}';
    }

    @Override
    public String toString() {
        return toSafeString();
    }

    public static class Builder {
        private DatabaseMode mode = DatabaseMode.IN_MEMORY;
        private String jdbcUrl = "jdbc:postgresql://localhost:5432/hireflow_dev";
        private String username = "hireflow_user";
        private String password = "";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeoutMs = 20000;
        private long idleTimeoutMs = 30000;
        private long maxLifetimeMs = 600000;
        private long leakDetectionThresholdMs = 10000;

        public Builder mode(DatabaseMode mode) {
            this.mode = mode != null ? mode : DatabaseMode.IN_MEMORY;
            return this;
        }

        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl cannot be null");
            return this;
        }

        public Builder username(String username) {
            this.username = Objects.requireNonNull(username, "username cannot be null");
            return this;
        }

        public Builder password(String password) {
            this.password = password != null ? password : "";
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = Math.max(1, maximumPoolSize);
            return this;
        }

        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = Math.max(0, minimumIdle);
            return this;
        }

        public Builder connectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = Math.max(1000, connectionTimeoutMs);
            return this;
        }

        public Builder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = Math.max(10000, idleTimeoutMs);
            return this;
        }

        public Builder maxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = Math.max(30000, maxLifetimeMs);
            return this;
        }

        public Builder leakDetectionThresholdMs(long leakDetectionThresholdMs) {
            this.leakDetectionThresholdMs = leakDetectionThresholdMs;
            return this;
        }

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}
