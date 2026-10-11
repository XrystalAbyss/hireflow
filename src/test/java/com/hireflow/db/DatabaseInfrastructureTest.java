package com.hireflow.db;

import com.hireflow.exception.DatabaseException;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.SchedulingConflictException;
import com.hireflow.exception.TransactionRetryableException;
import com.hireflow.exception.ValidationException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2B.1 Integration Test Suite for JDBC Database Infrastructure.
 * Validates:
 * 1. Successful database connection and health verification.
 * 2. Unavailable database handling with clean failure and bounded timeout.
 * 3. Connection pool lifecycle: creation, borrow, return, and clean shutdown.
 * 4. Successful transaction commit persists changes.
 * 5. Transaction rollback after failure leaves no orphaned state.
 * 6. Nested transaction behavior: explicit rejection with IllegalStateException.
 * 7. Connection cleanup after exceptions guarantees zero ThreadLocal or connection leaks.
 * 8. Isolation of simultaneous transaction contexts across concurrent threads.
 * 9. Automated retry loop for retryable failures vs immediate fail-fast for domain errors.
 * 10. Flyway migration status inspection via FlywayMigrationService.
 * 11. Active connection close suppression inside transactions (Requirement 8).
 * 12. Credential masking in DatabaseConfig.toString() and exception sanitization (Requirement 9).
 * 13. SQLSTATE translation across all domain error categories.
 * 14. TransactionManager getConnection semantics inside and outside transactions.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseInfrastructureTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    @BeforeAll
    static void setUpAll() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(5)
                .minimumIdle(1)
                .connectionTimeoutMs(5000)
                .build();

        try {
            dataSourceProvider = new DataSourceProvider(testConfig);
            dbAvailable = dataSourceProvider.verifyConnectionHealth(3);
            if (dbAvailable) {
                // Ensure migrations are initialized before testing
                FlywayMigrationService migrationService = new FlywayMigrationService(dataSourceProvider.getDataSource());
                migrationService.migrate();
                transactionManager = new TransactionManager(dataSourceProvider.getDataSource(), 3);
            }
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSourceProvider != null) {
            dataSourceProvider.close();
        }
    }

    @BeforeEach
    void ensureDatabaseAvailable() {
        assumeTrue(dbAvailable, "PostgreSQL test database not available at " + (testConfig != null ? testConfig.getJdbcUrl() : ""));
    }

    @Test
    @Order(1)
    @DisplayName("1. Successful database connection and health verification")
    void testSuccessfulDatabaseConnection() {
        assertTrue(dataSourceProvider.verifyConnectionHealth(5),
                "Database health check must return true for active test database");
    }

    @Test
    @Order(2)
    @DisplayName("2. Unavailable database handling with clean failure and bounded timeout")
    void testUnavailableDatabaseHandling() {
        DatabaseConfig invalidConfig = DatabaseConfig.builder()
                .jdbcUrl("jdbc:postgresql://localhost:59999/hireflow_test")
                .username("hireflow_user")
                .password("fake_password")
                .connectionTimeoutMs(1000)
                .build();

        try (DataSourceProvider invalidProvider = new DataSourceProvider(invalidConfig)) {
            assertFalse(invalidProvider.verifyConnectionHealth(1),
                    "Health check against unreachable database must return false");
        } catch (DatabaseException ex) {
            assertTrue(ex.getMessage().contains("Failed to initialize") || ex.getMessage().contains("unreachable")
                    || ex.getMessage().contains("connection"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. Connection pool lifecycle: creation, borrow, return, and clean shutdown")
    void testConnectionPoolLifecycle() throws Exception {
        DatabaseConfig tempConfig = DatabaseConfig.builder()
                .jdbcUrl(testConfig.getJdbcUrl())
                .username(testConfig.getUsername())
                .password(testConfig.getPassword())
                .maximumPoolSize(3)
                .minimumIdle(1)
                .connectionTimeoutMs(3000)
                .build();

        DataSourceProvider tempProvider = new DataSourceProvider(tempConfig);
        assertFalse(tempProvider.isClosed());

        try (Connection conn = tempProvider.getConnection()) {
            assertNotNull(conn);
            assertTrue(conn.isValid(2));
        }

        tempProvider.close();
        assertTrue(tempProvider.isClosed());
        assertThrows(IllegalStateException.class, tempProvider::getConnection,
                "Closed provider must throw IllegalStateException on connection request");
    }

    @Test
    @Order(4)
    @DisplayName("4. Successful transaction commit persists changes")
    void testSuccessfulTransactionCommit() throws Exception {
        String testCompanyId = "CMP-INFRA-COMMIT";

        transactionManager.runInTransactionWithoutResult(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO companies (id, name, industry) VALUES ('" +
                        testCompanyId + "', 'Commit Test Corp', 'Technology') ON CONFLICT DO NOTHING");
            }
        });

        try (Connection conn = dataSourceProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM companies WHERE id = '" + testCompanyId + "'")) {
            assertTrue(rs.next(), "Committed record must exist in database");
            assertEquals("Commit Test Corp", rs.getString("name"));
        }

        // Cleanup
        try (Connection conn = dataSourceProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM companies WHERE id = '" + testCompanyId + "'");
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. Transaction rollback after failure leaves no orphaned state")
    void testTransactionRollbackAfterFailure() throws Exception {
        String testCompanyId = "CMP-INFRA-ROLLBACK";

        assertThrows(RuntimeException.class, () -> {
            transactionManager.runInTransactionWithoutResult(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("INSERT INTO companies (id, name, industry) VALUES ('" +
                            testCompanyId + "', 'Rollback Corp', 'Technology')");
                }
                throw new RuntimeException("Simulated mid-transaction failure");
            });
        });

        try (Connection conn = dataSourceProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM companies WHERE id = '" + testCompanyId + "'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Rolled back record must not exist in database");
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. Nested transaction behavior: explicit rejection with IllegalStateException")
    void testNestedTransactionBehavior() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transactionManager.runInTransactionWithoutResult(outerConn -> {
                transactionManager.runInTransactionWithoutResult(innerConn -> {
                    fail("Nested transaction must not be executed");
                });
            });
        });

        assertTrue(ex.getMessage().contains("Nested transactions are not supported"),
                "Exception message must clearly identify unsupported nested transaction");
    }

    @Test
    @Order(7)
    @DisplayName("7. Connection cleanup after exceptions guarantees zero ThreadLocal or connection leaks")
    void testConnectionCleanupAfterExceptions() {
        assertFalse(transactionManager.isTransactionActive(), "Initial state must have no active transaction");
        assertNull(transactionManager.getActiveConnection(), "Initial active connection must be null");

        try {
            transactionManager.runInTransactionWithoutResult(conn -> {
                assertTrue(transactionManager.isTransactionActive(), "Transaction must be active inside block");
                assertSame(conn, transactionManager.getActiveConnection(), "Active connection must match current connection");
                throw new RuntimeException("Simulated error to test cleanup");
            });
        } catch (RuntimeException ignored) {
        }

        assertFalse(transactionManager.isTransactionActive(), "ThreadLocal transaction flag must be cleared");
        assertNull(transactionManager.getActiveConnection(), "Active connection must be cleared from ThreadLocal");
    }

    @Test
    @Order(8)
    @DisplayName("8. Isolation of simultaneous transaction contexts across concurrent threads")
    void testSimultaneousTransactionContextIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch continueLatch = new CountDownLatch(1);

        Future<Connection> f1 = executor.submit(() ->
                transactionManager.runInTransaction(conn1 -> {
                    bothStarted.countDown();
                    continueLatch.await(5, TimeUnit.SECONDS);
                    return conn1;
                })
        );

        Future<Connection> f2 = executor.submit(() ->
                transactionManager.runInTransaction(conn2 -> {
                    bothStarted.countDown();
                    continueLatch.await(5, TimeUnit.SECONDS);
                    return conn2;
                })
        );

        assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
        continueLatch.countDown();

        Connection conn1 = f1.get(5, TimeUnit.SECONDS);
        Connection conn2 = f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(conn1);
        assertNotNull(conn2);
        assertNotSame(conn1, conn2, "Concurrent threads must receive independent physical connections");
    }

    @Test
    @Order(9)
    @DisplayName("9. Automated retry loop for retryable failures vs immediate fail-fast for domain errors")
    void testRetryBehaviorForRetryableFailures() {
        // Case A: Retryable failure succeeds after 2 retries
        AtomicInteger attemptCounter = new AtomicInteger(0);

        String result = transactionManager.runInTransaction(conn -> {
            int attempt = attemptCounter.incrementAndGet();
            if (attempt < 3) {
                throw new TransactionRetryableException("Simulated deadlock anomaly", null, "40P01");
            }
            return "SUCCESS_ON_ATTEMPT_3";
        });

        assertEquals("SUCCESS_ON_ATTEMPT_3", result);
        assertEquals(3, attemptCounter.get(), "TransactionManager must have executed 3 attempts (2 retries + 1 success)");

        // Case B: Non-retryable error (ValidationException) fails fast on attempt 1
        AtomicInteger nonRetryCounter = new AtomicInteger(0);

        ValidationException valEx = assertThrows(ValidationException.class, () ->
                transactionManager.runInTransactionWithoutResult(conn -> {
                    nonRetryCounter.incrementAndGet();
                    throw new ValidationException("Invalid student CGPA");
                })
        );

        assertEquals("Invalid student CGPA", valEx.getMessage());
        assertEquals(1, nonRetryCounter.get(), "Non-retryable exceptions must NOT be retried");
    }

    @Test
    @Order(10)
    @DisplayName("10. Flyway migration status inspection via FlywayMigrationService")
    void testFlywayMigrationStatus() {
        FlywayMigrationService migrationService = new FlywayMigrationService(dataSourceProvider.getDataSource());
        assertFalse(migrationService.isBaselineOnMigrate(), "Default Flyway migration service must have baselineOnMigrate=false");

        FlywayMigrationService customService = new FlywayMigrationService(dataSourceProvider.getDataSource(), true);
        assertTrue(customService.isBaselineOnMigrate(), "Explicitly configured Flyway migration service must have baselineOnMigrate=true");

        MigrationInfo current = migrationService.getInfo().current();

        assertNotNull(current, "Current migration must not be null");
        assertEquals("1", current.getVersion().getVersion(), "Current migration version must be 1");
        assertEquals("initial schema", current.getDescription());
        assertTrue(current.getState().isApplied(), "Migration state must be APPLIED");
    }

    @Test
    @Order(11)
    @DisplayName("11. Active connection close suppression prevents premature pool return by repository code")
    void testActiveConnectionCloseSuppressionInsideTransaction() throws Exception {
        String testCompanyId = "CMP-INFRA-CLOSE-SUPPRESS";

        // Within transaction, simulate a repository closing its connection
        transactionManager.runInTransactionWithoutResult(conn -> {
            try (Statement s1 = conn.createStatement()) {
                s1.executeUpdate("INSERT INTO companies (id, name, industry) VALUES ('" +
                        testCompanyId + "', 'Close Suppress Corp', 'Consulting') ON CONFLICT DO NOTHING");
            }

            // Simulate repository calling conn.close()
            conn.close();

            // Connection should still be open and usable because close was suppressed
            assertFalse(conn.isClosed(), "Active transaction connection proxy must remain open after close() call");

            try (Statement s2 = conn.createStatement()) {
                s2.executeUpdate("UPDATE companies SET industry = 'Finance' WHERE id = '" + testCompanyId + "'");
            }
        });

        // Verify changes were successfully committed
        try (Connection verifyConn = dataSourceProvider.getConnection();
             Statement stmt = verifyConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT industry FROM companies WHERE id = '" + testCompanyId + "'")) {
            assertTrue(rs.next());
            assertEquals("Finance", rs.getString("industry"));
        }

        // Cleanup
        try (Connection cleanupConn = dataSourceProvider.getConnection();
             Statement stmt = cleanupConn.createStatement()) {
            stmt.executeUpdate("DELETE FROM companies WHERE id = '" + testCompanyId + "'");
        }
    }

    @Test
    @Order(12)
    @DisplayName("12. Credentials are never leaked in DatabaseConfig.toString() or exception messages")
    void testCredentialsNotLeakedInConfigToStringOrExceptions() {
        DatabaseConfig secretConfig = DatabaseConfig.builder()
                .jdbcUrl("jdbc:postgresql://admin:super_secret_pass@localhost:5432/hireflow_dev")
                .username("admin")
                .password("super_secret_pass")
                .build();

        String safeStr = secretConfig.toString();
        assertFalse(safeStr.contains("super_secret_pass"), "DatabaseConfig.toString() must never contain raw password");
        assertTrue(safeStr.contains("password='***'"), "Password must be masked in string representation");

        SQLException sqlEx = new SQLException("Connection to jdbc:postgresql://admin:super_secret_pass@localhost failed", "08001");
        RuntimeException translated = SQLExceptionTranslator.translate("DB Connection", sqlEx);
        assertFalse(translated.getMessage().contains("super_secret_pass"), "Translated exception must sanitize credentials");
    }

    @Test
    @Order(13)
    @DisplayName("13. SQLSTATE translation across domain exceptions")
    void testSQLExceptionTranslationForVariousSqlStates() {
        // 23505 -> DuplicateEntityException
        SQLException uniqueEx = new SQLException("Key (id)=(1) already exists", "23505");
        assertInstanceOf(DuplicateEntityException.class, SQLExceptionTranslator.translate("Insert", uniqueEx));

        // 23503 -> ValidationException
        SQLException fkEx = new SQLException("Key (dept_id)=(99) not found", "23503");
        assertInstanceOf(ValidationException.class, SQLExceptionTranslator.translate("Insert", fkEx));

        // 23514 -> ValidationException
        SQLException checkEx = new SQLException("Check constraint cgpa >= 0.0 violated", "23514");
        assertInstanceOf(ValidationException.class, SQLExceptionTranslator.translate("Insert", checkEx));

        // 23P01 -> SchedulingConflictException
        SQLException exclusionEx = new SQLException("Exclusion constraint violated", "23P01");
        assertInstanceOf(SchedulingConflictException.class, SQLExceptionTranslator.translate("Schedule", exclusionEx));

        // 40P01 -> TransactionRetryableException
        SQLException deadlockEx = new SQLException("deadlock detected", "40P01");
        assertInstanceOf(TransactionRetryableException.class, SQLExceptionTranslator.translate("Lock", deadlockEx));
        assertTrue(SQLExceptionTranslator.isRetryable(deadlockEx));

        // 40001 -> TransactionRetryableException
        SQLException serialEx = new SQLException("could not serialize access", "40001");
        assertInstanceOf(TransactionRetryableException.class, SQLExceptionTranslator.translate("Serialize", serialEx));
        assertTrue(SQLExceptionTranslator.isRetryable(serialEx));
    }

    @Test
    @Order(14)
    @DisplayName("14. TransactionManager getConnection semantics inside and outside transactions")
    void testTransactionManagerGetConnectionSemantics() throws Exception {
        // Outside transaction: returns fresh standalone connection that can be closed
        try (Connection standaloneConn = transactionManager.getConnection()) {
            assertNotNull(standaloneConn);
            assertFalse(transactionManager.isTransactionActive());
        }

        // Inside transaction: returns transaction proxy matching active connection
        transactionManager.runInTransactionWithoutResult(conn -> {
            Connection current = transactionManager.getConnection();
            assertNotNull(current);
            assertSame(conn, current, "getConnection inside transaction must return the ambient transactional proxy");
        });
    }

    @Test
    @Order(15)
    @DisplayName("15. Active transaction connection restricts manual commit, rollback, setAutoCommit, and setTransactionIsolation")
    void testActiveTransactionConnectionRestrictsManualDemarcation() {
        transactionManager.runInTransactionWithoutResult(conn -> {
            // 1. Calling commit() directly throws UnsupportedOperationException
            UnsupportedOperationException commitEx = assertThrows(UnsupportedOperationException.class, conn::commit);
            assertTrue(commitEx.getMessage().contains("commit() cannot be invoked directly"));

            // 2. Calling rollback() directly throws UnsupportedOperationException
            UnsupportedOperationException rbEx = assertThrows(UnsupportedOperationException.class, (org.junit.jupiter.api.function.Executable) conn::rollback);
            assertTrue(rbEx.getMessage().contains("rollback() cannot be invoked directly"));

            // 3. Calling setAutoCommit() throws UnsupportedOperationException
            UnsupportedOperationException autoCommitEx = assertThrows(UnsupportedOperationException.class, () -> conn.setAutoCommit(true));
            assertTrue(autoCommitEx.getMessage().contains("setAutoCommit() cannot be invoked"));

            // 4. Calling setTransactionIsolation() throws UnsupportedOperationException
            UnsupportedOperationException isoEx = assertThrows(UnsupportedOperationException.class,
                    () -> conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE));
            assertTrue(isoEx.getMessage().contains("setTransactionIsolation() cannot be invoked"));

            // 5. Unwrapping to Connection.class returns the protected proxy, preventing demarcation bypass
            try {
                Connection unwrapped = conn.unwrap(Connection.class);
                assertSame(conn, unwrapped);
            } catch (SQLException ex) {
                fail("Unwrap should succeed without exception", ex);
            }
        });
    }
}

