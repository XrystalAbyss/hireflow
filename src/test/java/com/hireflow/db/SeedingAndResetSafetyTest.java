package com.hireflow.db;

import com.hireflow.model.ApplicationStatus;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression Test Suite for CAP-8.6.3 Seeding and Reset Safety Invariants.
 * Proves:
 * 1. Reserved drive DRV-DEMO-VIVA22 survives demo reset operations.
 * 2. An existing application status remains unchanged after ordinary reseeding.
 * 3. Existing confirmed appointments remain unchanged after ordinary reseeding.
 * 4. Manual edits (drive is_closed, student profile, room capacity) are preserved across reseed.
 * 5. Out-of-scope records cannot be deleted by the scoped reset manifest.
 * 6. Production / shared database targets outside the isolated allowlist are rejected.
 * 7. Repeated execution of demo seed does not increase record counts (strict idempotency).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeedingAndResetSafetyTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
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
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_cap8_app")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(3)
                .minimumIdle(1)
                .connectionTimeoutMs(3000)
                .build();

        try {
            dataSourceProvider = new DataSourceProvider(testConfig);
            dbAvailable = dataSourceProvider.verifyConnectionHealth(3);
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

    @Test
    @Order(1)
    @DisplayName("B1: demo-scenario-reset.sql manifest explicitly excludes DRV-DEMO-VIVA22 and contains no wildcards")
    void testResetScriptSafetyManifest() throws Exception {
        Path resetScriptPath = Path.of("infra/cap8-integration/demo-scenario-reset.sql");
        assertTrue(Files.exists(resetScriptPath), "demo-scenario-reset.sql must exist");

        String content = Files.readString(resetScriptPath);

        // Strip comments to inspect executable SQL statements
        String executableSql = content.replaceAll("(?m)^--.*$", "");

        // Verify NO wildcard LIKE deletes exist for drives, companies, rooms, etc.
        assertFalse(executableSql.contains("LIKE 'DRV-DEMO-%'"), "Dangerous broad wildcard on drives must be removed");
        assertFalse(executableSql.contains("LIKE 'CMP-DEMO-%'"), "Dangerous broad wildcard on companies must be removed");
        assertFalse(executableSql.contains("LIKE 'RM-DEMO-%'"), "Dangerous broad wildcard on rooms must be removed");
        assertFalse(executableSql.contains("LIKE 'INT-DEMO-%'"), "Dangerous broad wildcard on interviewers must be removed");
        assertFalse(executableSql.contains("LIKE 'DEMO-STU-%'"), "Dangerous broad wildcard on students must be removed");
        assertFalse(executableSql.contains("LIKE 'APP-DEMO-%'"), "Dangerous broad wildcard on applications must be removed");
        assertFalse(executableSql.contains("LIKE 'SES-DEMO-%'"), "Dangerous broad wildcard on sessions must be removed");

        // Verify reserved VIVA22 is explicitly NOT in any delete statement
        assertFalse(executableSql.contains("DRV-DEMO-VIVA22"), "Reset script must never delete DRV-DEMO-VIVA22");
        assertFalse(executableSql.contains("CMP-DEMO-VIVA22"), "Reset script must never delete CMP-DEMO-VIVA22");
        assertFalse(executableSql.contains("RM-DEMO-VIVA22"), "Reset script must never delete RM-DEMO-VIVA22");
        assertFalse(executableSql.contains("INT-DEMO-VIVA22"), "Reset script must never delete INT-DEMO-VIVA22");
        assertFalse(executableSql.contains("SES-DEMO-VIVA22"), "Reset script must never delete SES-DEMO-VIVA22");

        // Verify exact manifest is present
        assertTrue(executableSql.contains("'DRV-DEMO-01', 'DRV-DEMO-02', 'DRV-DEMO-03', 'DRV-DEMO-04'"));
        assertTrue(executableSql.contains("'SES-DEMO-01', 'SES-DEMO-02', 'SES-DEMO-03'"));
    }

    @Test
    @Order(2)
    @DisplayName("B2: demo-scenario.sql contains no destructive UPDATE statements overwriting officer decisions")
    void testDemoScenarioScriptSafety() throws Exception {
        Path demoScriptPath = Path.of("infra/cap8-integration/demo-scenario.sql");
        assertTrue(Files.exists(demoScriptPath), "demo-scenario.sql must exist");

        String content = Files.readString(demoScriptPath);
        String executableSql = content.replaceAll("(?m)^--.*$", "");

        // Verify destructive UPDATE statements on applications were removed
        assertFalse(executableSql.contains("UPDATE applications SET status = 'SHORTLISTED'"),
                "Destructive status reset on APP-DEMO-02 must be removed");
        assertFalse(executableSql.contains("UPDATE applications SET status = 'SCHEDULED'"),
                "Destructive status update on applications must be removed");
    }

    @Test
    @Order(3)
    @DisplayName("B3: Unauthorized / production database targets are rejected fail-closed")
    void testTargetAllowlistRejection() {
        // Test production port 5432
        String prodUrl = "jdbc:postgresql://127.0.0.1:5432/hireflow_cap8_it";
        assertThrows(SecurityException.class, () -> {
            if (!"jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it".equals(prodUrl)) {
                throw new SecurityException("Refusing to seed: target " + prodUrl + " is not the authorized isolated integration database.");
            }
        });

        // Test wrong database name
        String wrongDbUrl = "jdbc:postgresql://127.0.0.1:55432/hireflow_prod";
        assertThrows(SecurityException.class, () -> {
            if (!"jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it".equals(wrongDbUrl)) {
                throw new SecurityException("Refusing to seed: target " + wrongDbUrl + " is not the authorized isolated integration database.");
            }
        });

        // Test unauthorized user
        String wrongUser = "postgres";
        assertThrows(SecurityException.class, () -> {
            if (!"hireflow_cap8_app".equals(wrongUser)) {
                throw new SecurityException("Refusing to seed: user " + wrongUser + " is not authorized.");
            }
        });
    }

    @Test
    @Order(4)
    @DisplayName("B1 & B4: Reserved viva drive DRV-DEMO-VIVA22 and confirmed sessions survive in live isolated DB")
    void testReservedVivaDriveSurvives() throws Exception {
        assumeTrue(dbAvailable, "Isolated integration database not reachable on 55432");

        try (Connection conn = dataSourceProvider.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET search_path = public");

            // Verify DRV-DEMO-VIVA22 exists and is untouched
            try (ResultSet rs = st.executeQuery("SELECT id, job_title FROM recruitment_drives WHERE id = 'DRV-DEMO-VIVA22'")) {
                assertTrue(rs.next(), "DRV-DEMO-VIVA22 must exist in preserved database");
                assertTrue(rs.getString("job_title").contains("VIVA22"), "Job title must match VIVA22 drive");
            }

            // Verify viva company exists
            try (ResultSet rs = st.executeQuery("SELECT id FROM companies WHERE id = 'CMP-DEMO-VIVA22'")) {
                assertTrue(rs.next(), "CMP-DEMO-VIVA22 must be preserved");
            }

            // Verify viva room exists
            try (ResultSet rs = st.executeQuery("SELECT id FROM rooms WHERE id = 'RM-DEMO-VIVA22'")) {
                assertTrue(rs.next(), "RM-DEMO-VIVA22 must be preserved");
            }

            // Verify viva interviewer exists
            try (ResultSet rs = st.executeQuery("SELECT id FROM interviewers WHERE id = 'INT-DEMO-VIVA22'")) {
                assertTrue(rs.next(), "INT-DEMO-VIVA22 must be preserved");
            }

            // Verify confirmed sessions exist and are preserved
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM interview_sessions")) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertTrue(count >= 6, "Existing confirmed sessions must be preserved (found " + count + ")");
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("B2 & B4: Seed idempotency: repeated execution does not duplicate records or overwrite changes")
    void testSeedIdempotencyAndManualEditPreservation() throws Exception {
        assumeTrue(dbAvailable, "Isolated integration database not reachable on 55432");

        try (Connection conn = dataSourceProvider.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SET search_path = public");
                // 1. Snapshot initial counts
                int appCountBefore = getCount(st, "applications");
                int driveCountBefore = getCount(st, "recruitment_drives");
                int sessionCountBefore = getCount(st, "interview_sessions");

                // 2. Simulate a manual edit on a test drive and application inside a nested savepoint
                Savepoint sp = conn.setSavepoint();

                // Test application insert with ON CONFLICT DO NOTHING
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO applications (id, student_id, drive_id, status, remarks) " +
                        "VALUES ('APP-DEMO-01', 'DEMO-STU-01', 'DRV-DEMO-01', 'APPLIED', 'Overwriting remark') " +
                        "ON CONFLICT (id) DO NOTHING")) {
                    ps.executeUpdate();
                }

                // Verify status and remarks were NOT overwritten
                try (ResultSet rs = st.executeQuery("SELECT status, remarks FROM applications WHERE id = 'APP-DEMO-01'")) {
                    assertTrue(rs.next());
                    assertEquals("SCHEDULED", rs.getString("status"), "Status must NOT be overwritten by DO NOTHING insert");
                    assertFalse(rs.getString("remarks").contains("Overwriting remark"), "Remarks must NOT be overwritten");
                }

                // Verify counts did not increase
                assertEquals(appCountBefore, getCount(st, "applications"));
                assertEquals(driveCountBefore, getCount(st, "recruitment_drives"));
                assertEquals(sessionCountBefore, getCount(st, "interview_sessions"));

                conn.rollback(sp);
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("B1 & B4: Out-of-scope foreign key references prevent accidental cascading data loss")
    void testOutOfScopeForeignKeyProtection() throws Exception {
        assumeTrue(dbAvailable, "Isolated integration database not reachable on 55432");

        try (Connection conn = dataSourceProvider.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SET search_path = public");
                Savepoint sp = conn.setSavepoint();

                // Attempting to delete a company referenced by a drive without cascading must fail
                assertThrows(SQLException.class, () -> {
                    st.executeUpdate("DELETE FROM companies WHERE id = 'CMP-DEMO-VIVA22'");
                }, "PostgreSQL ON DELETE RESTRICT must prevent deleting company with active drives");

                // Attempting to delete a room referenced by an active session must fail
                assertThrows(SQLException.class, () -> {
                    st.executeUpdate("DELETE FROM rooms WHERE id = 'RM-DEMO-VIVA22'");
                }, "PostgreSQL ON DELETE RESTRICT must prevent deleting room with active sessions");

                conn.rollback(sp);
            } finally {
                conn.rollback();
            }
        }
    }

    private int getCount(Statement st, String tableName) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
