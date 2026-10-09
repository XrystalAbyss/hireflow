package com.hireflow.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2A Database Validation Test Suite.
 * Verifies:
 * 1. Flyway migration tooling execution and flyway_schema_history records.
 * 2. Two-connection concurrent publication competitions (interviewer, room, student).
 * 3. Buffer gap violation detection under pessimistic locking.
 * 4. Atomicity and rollback on partial failure.
 * 5. Authentic deadlock generation (SQLSTATE 40P01) and deadlock-free deterministic locking.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FlywayAndConcurrencyTest {

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASS;

    static {
        java.util.Properties localProps = new java.util.Properties();
        java.io.File localFile = new java.io.File("hireflow-local.properties");
        if (localFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }
        DB_URL = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        DB_USER = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        DB_PASS = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));
    }

    private static boolean dbAvailable = false;

    @BeforeAll
    static void checkDatabaseAvailability() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            dbAvailable = true;
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @BeforeEach
    void assumeDatabaseAvailable() {
        assumeTrue(dbAvailable, "PostgreSQL test database not accessible at " + DB_URL);
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // -------------------------------------------------------------------------
    // PART A: Clean Database Migration using Flyway Migration Tooling
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Part A: Execute V1 using actual Flyway tooling from clean database and verify flyway_schema_history")
    void testFlywayMigrationToolingExecution() throws Exception {
        // 1. Clean the schema to prove reproducible migration from fresh state
        Flyway flywayClean = Flyway.configure()
                .dataSource(DB_URL, DB_USER, DB_PASS)
                .cleanDisabled(false)
                .locations("classpath:db/migration")
                .load();
        flywayClean.clean();

        // 2. Execute Flyway migration
        Flyway flyway = Flyway.configure()
                .dataSource(DB_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        // 3. Verify Flyway reported success
        assertTrue(result.success, "Flyway migration must report success");
        assertEquals(1, result.migrationsExecuted, "Exactly 1 versioned migration must be executed");
        assertEquals("1", result.targetSchemaVersion, "Target schema version must be 1");

        // 4. Verify flyway_schema_history table
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT version, description, type, script, success FROM flyway_schema_history WHERE version = '1'")) {
            assertTrue(rs.next(), "flyway_schema_history must contain an entry for version 1");
            assertEquals("initial schema", rs.getString("description"));
            assertEquals("SQL", rs.getString("type"));
            assertEquals("V1__initial_schema.sql", rs.getString("script"));
            assertTrue(rs.getBoolean("success"), "Migration status in history must be true");
        }

        // 5. Seed development data for subsequent concurrency tests
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            // Seed companies
            stmt.executeUpdate("INSERT INTO companies (id, name, industry) VALUES " +
                    "('CMP-001', 'Google Cloud', 'Cloud Computing'), " +
                    "('CMP-002', 'Microsoft', 'Enterprise Software') ON CONFLICT DO NOTHING");
            // Seed students
            stmt.executeUpdate("INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) VALUES " +
                    "('STU-101', 'Alice Smith', 'alice@univ.edu', 'CSE', 9.20, 2026, TRUE), " +
                    "('STU-102', 'Bob Jones', 'bob@univ.edu', 'CSE', 8.40, 2026, TRUE), " +
                    "('STU-104', 'Diana Prince', 'diana@univ.edu', 'ECE', 8.90, 2026, TRUE) ON CONFLICT DO NOTHING");
            // Seed drives
            stmt.executeUpdate("INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline) VALUES " +
                    "('DRV-101', 'CMP-001', 'SDE-1', 8.00, CURRENT_DATE + 14), " +
                    "('DRV-102', 'CMP-002', 'Cloud Engineer', 7.50, CURRENT_DATE + 21) ON CONFLICT DO NOTHING");
            // Seed interviewers
            stmt.executeUpdate("INSERT INTO interviewers (id, name, company_id) VALUES " +
                    "('INT-001', 'Dr. Sarah Chen', 'CMP-001'), " +
                    "('INT-002', 'Marcus Vance', 'CMP-002') ON CONFLICT DO NOTHING");
            // Seed rooms
            stmt.executeUpdate("INSERT INTO rooms (id, name, capacity) VALUES " +
                    "('RM-101', 'Tech Lab A - Booth 1', 2), " +
                    "('RM-102', 'Tech Lab A - Booth 2', 2) ON CONFLICT DO NOTHING");
            // Seed applications
            stmt.executeUpdate("INSERT INTO applications (id, student_id, drive_id, status) VALUES " +
                    "('APP-101', 'STU-101', 'DRV-101', 'SHORTLISTED'), " +
                    "('APP-102', 'STU-102', 'DRV-101', 'SHORTLISTED'), " +
                    "('APP-104', 'STU-101', 'DRV-102', 'SHORTLISTED') ON CONFLICT DO NOTHING");
        }
    }

    // -------------------------------------------------------------------------
    // PART B: Real Two-Connection Concurrency Verifications
    // -------------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("Part B.1: Two transactions competing for the same interviewer and time window")
    void testConcurrentInterviewerCompetition() throws Exception {
        try (Connection conn1 = createConnection();
             Connection conn2 = createConnection()) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Conn 1 books INT-001 from 09:00 to 09:45 in RM-101
            try (PreparedStatement ps1 = conn1.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps1.setString(1, "SES-INT-001");
                ps1.setString(2, "APP-101");
                ps1.setString(3, "STU-101");
                ps1.setString(4, "INT-001");
                ps1.setString(5, "RM-101");
                ps1.setString(6, "2026-10-20 09:00:00");
                ps1.setString(7, "2026-10-20 09:45:00");
                ps1.executeUpdate();
            }
            conn1.commit(); // Committed into database

            // Conn 2 attempts booking same INT-001 at overlapping 09:30 - 10:15 in different room RM-102
            SQLException thrown = null;
            try (PreparedStatement ps2 = conn2.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps2.setString(1, "SES-INT-002");
                ps2.setString(2, "APP-102");
                ps2.setString(3, "STU-102");
                ps2.setString(4, "INT-001"); // Conflict!
                ps2.setString(5, "RM-102");
                ps2.setString(6, "2026-10-20 09:30:00");
                ps2.setString(7, "2026-10-20 10:15:00");
                ps2.executeUpdate();
                conn2.commit();
            } catch (SQLException ex) {
                conn2.rollback();
                thrown = ex;
            }

            assertNotNull(thrown, "Expected overlapping interviewer booking to be rejected");
            assertEquals("23P01", thrown.getSQLState(), "Expected exclusion_violation (23P01)");
            assertTrue(thrown.getMessage().contains("no_overlapping_interviewer_sessions"),
                    "Error must reference interviewer exclusion constraint");

            // Clean up
            try (Statement cleanup = conn1.createStatement()) {
                cleanup.executeUpdate("DELETE FROM interview_sessions WHERE id = 'SES-INT-001'");
            }
            conn1.commit();
        }
    }

    @Test
    @Order(3)
    @DisplayName("Part B.2: Two transactions competing for the same room and time window")
    void testConcurrentRoomCompetition() throws Exception {
        try (Connection conn1 = createConnection();
             Connection conn2 = createConnection()) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Conn 1 books RM-101 from 10:00 to 10:45 with INT-001
            try (PreparedStatement ps1 = conn1.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps1.setString(1, "SES-RM-001");
                ps1.setString(2, "APP-101");
                ps1.setString(3, "STU-101");
                ps1.setString(4, "INT-001");
                ps1.setString(5, "RM-101");
                ps1.setString(6, "2026-10-20 10:00:00");
                ps1.setString(7, "2026-10-20 10:45:00");
                ps1.executeUpdate();
            }
            conn1.commit();

            // Conn 2 attempts booking same RM-101 at overlapping 10:30 - 11:15 with INT-002
            SQLException thrown = null;
            try (PreparedStatement ps2 = conn2.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps2.setString(1, "SES-RM-002");
                ps2.setString(2, "APP-102");
                ps2.setString(3, "STU-102");
                ps2.setString(4, "INT-002");
                ps2.setString(5, "RM-101"); // Conflict!
                ps2.setString(6, "2026-10-20 10:30:00");
                ps2.setString(7, "2026-10-20 11:15:00");
                ps2.executeUpdate();
                conn2.commit();
            } catch (SQLException ex) {
                conn2.rollback();
                thrown = ex;
            }

            assertNotNull(thrown, "Expected overlapping room booking to be rejected");
            assertEquals("23P01", thrown.getSQLState(), "Expected exclusion_violation (23P01)");
            assertTrue(thrown.getMessage().contains("no_overlapping_room_sessions"),
                    "Error must reference room exclusion constraint");

            // Clean up
            try (Statement cleanup = conn1.createStatement()) {
                cleanup.executeUpdate("DELETE FROM interview_sessions WHERE id = 'SES-RM-001'");
            }
            conn1.commit();
        }
    }

    @Test
    @Order(4)
    @DisplayName("Part B.3: Two different applications belonging to the same student at overlapping times")
    void testConcurrentStudentCrossDriveCompetition() throws Exception {
        try (Connection conn1 = createConnection();
             Connection conn2 = createConnection()) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Alice (STU-101) has APP-101 (Google) and APP-104 (Microsoft)
            // Conn 1 books Alice for Google from 11:00 to 11:45 in RM-101 with INT-001
            try (PreparedStatement ps1 = conn1.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps1.setString(1, "SES-STU-001");
                ps1.setString(2, "APP-101");
                ps1.setString(3, "STU-101");
                ps1.setString(4, "INT-001");
                ps1.setString(5, "RM-101");
                ps1.setString(6, "2026-10-20 11:00:00");
                ps1.setString(7, "2026-10-20 11:45:00");
                ps1.executeUpdate();
            }
            conn1.commit();

            // Conn 2 attempts booking Alice for Microsoft from 11:30 to 12:15 in RM-102 with INT-002
            SQLException thrown = null;
            try (PreparedStatement ps2 = conn2.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps2.setString(1, "SES-STU-002");
                ps2.setString(2, "APP-104"); // Different application!
                ps2.setString(3, "STU-101"); // Same student! Conflict!
                ps2.setString(4, "INT-002");
                ps2.setString(5, "RM-102");
                ps2.setString(6, "2026-10-20 11:30:00");
                ps2.setString(7, "2026-10-20 12:15:00");
                ps2.executeUpdate();
                conn2.commit();
            } catch (SQLException ex) {
                conn2.rollback();
                thrown = ex;
            }

            assertNotNull(thrown, "Expected overlapping student cross-drive booking to be rejected");
            assertEquals("23P01", thrown.getSQLState(), "Expected exclusion_violation (23P01)");
            assertTrue(thrown.getMessage().contains("no_overlapping_student_sessions"),
                    "Error must reference student exclusion constraint");

            // Clean up
            try (Statement cleanup = conn1.createStatement()) {
                cleanup.executeUpdate("DELETE FROM interview_sessions WHERE id = 'SES-STU-001'");
            }
            conn1.commit();
        }
    }

    @Test
    @Order(5)
    @DisplayName("Part B.4: Two non-overlapping appointments violating a 15-minute buffer under locking protocol")
    void testBufferGapConflictDetectionUnderLocking() throws Exception {
        try (Connection conn1 = createConnection();
             Connection conn2 = createConnection()) {

            conn1.setAutoCommit(false);
            conn2.setAutoCommit(false);

            // Transaction 1 locks RM-101, validates, and publishes 13:00 - 13:45 (requires buffer until 14:00)
            try (Statement stmt1 = conn1.createStatement()) {
                stmt1.executeQuery("SELECT id FROM rooms WHERE id = 'RM-101' FOR UPDATE");
                stmt1.executeUpdate("INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                        "VALUES ('SES-BUF-001', 'APP-101', 'STU-101', 'INT-001', 'RM-101', '2026-10-20 13:00:00', '2026-10-20 13:45:00')");
                stmt1.executeUpdate("UPDATE applications SET status = 'SCHEDULED' WHERE id = 'APP-101'");
            }
            conn1.commit(); // Committed

            // Transaction 2 proposes 13:50 - 14:35 in RM-101 (5 min gap, violates 15m buffer)
            boolean bufferConflictDetected = false;
            try (Statement stmt2 = conn2.createStatement()) {
                stmt2.executeQuery("SELECT id FROM rooms WHERE id = 'RM-101' FOR UPDATE");

                // Evaluate buffer condition against locked confirmed pool
                try (ResultSet rs = stmt2.executeQuery(
                        "SELECT COUNT(*) FROM interview_sessions " +
                                "WHERE room_id = 'RM-101' " +
                                "AND start_time < ('2026-10-20 14:35:00'::timestamp + INTERVAL '15 minutes') " +
                                "AND (end_time + INTERVAL '15 minutes') > '2026-10-20 13:50:00'::timestamp")) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        bufferConflictDetected = true;
                    }
                }
            }
            conn2.rollback(); // Rejected due to buffer collision

            assertTrue(bufferConflictDetected, "Transaction 2 must detect buffer conflict with Session 1 (ends at 13:45, buffer requires 14:00)");

            // Clean up
            try (Statement cleanup = conn1.createStatement()) {
                cleanup.executeUpdate("DELETE FROM interview_sessions WHERE id = 'SES-BUF-001'");
                cleanup.executeUpdate("UPDATE applications SET status = 'SHORTLISTED' WHERE id = 'APP-101'");
            }
            conn1.commit();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Part B.5: Transaction failing after session insertion but before application status update rolls back completely")
    void testAtomicityAndRollbackOnPartialFailure() throws Exception {
        try (Connection conn = createConnection()) {
            conn.setAutoCommit(false);

            // 1. Insert session
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)")) {
                ps.setString(1, "SES-FAIL-001");
                ps.setString(2, "APP-102");
                ps.setString(3, "STU-102");
                ps.setString(4, "INT-002");
                ps.setString(5, "RM-102");
                ps.setString(6, "2026-10-20 15:00:00");
                ps.setString(7, "2026-10-20 15:45:00");
                ps.executeUpdate();
            }

            // 2. Simulate failure before application status update commits (trigger rollback)
            conn.rollback();

            // 3. Verify in independent connection that session does NOT exist and application status remained unchanged
            try (Connection verifyConn = createConnection();
                 Statement stmt = verifyConn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM interview_sessions WHERE id = 'SES-FAIL-001'")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "Session must not exist after rollback");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT status FROM applications WHERE id = 'APP-102'")) {
                    assertTrue(rs.next());
                    assertEquals("SHORTLISTED", rs.getString(1), "Application status must remain SHORTLISTED");
                }
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("Part B.6: Two transactions competing with circular lock orders produce real PostgreSQL deadlock (SQLSTATE 40P01)")
    void testRealDeadlockDetectionUnderConflictingOrder() throws Exception {
        CountDownLatch t1LockedRoom1 = new CountDownLatch(1);
        CountDownLatch t2LockedRoom2 = new CountDownLatch(1);
        AtomicReference<SQLException> deadlockException = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Locks RM-101 first, then tries to lock RM-102
        Future<?> f1 = executor.submit(() -> {
            try (Connection conn1 = createConnection()) {
                conn1.setAutoCommit(false);
                try (Statement s1 = conn1.createStatement()) {
                    s1.executeQuery("SELECT id FROM rooms WHERE id = 'RM-101' FOR UPDATE");
                    t1LockedRoom1.countDown();

                    // Wait until Thread 2 has locked RM-102
                    assertTrue(t2LockedRoom2.await(5, TimeUnit.SECONDS));

                    // Now try to lock RM-102 (will block waiting for Thread 2)
                    s1.executeQuery("SELECT id FROM rooms WHERE id = 'RM-102' FOR UPDATE");
                    conn1.commit();
                }
            } catch (SQLException ex) {
                deadlockException.compareAndSet(null, ex);
            } catch (Exception ex) {
                fail(ex);
            }
        });

        // Thread 2: Locks RM-102 first (REVERSE ORDER), then tries to lock RM-101
        Future<?> f2 = executor.submit(() -> {
            try (Connection conn2 = createConnection()) {
                conn2.setAutoCommit(false);
                try (Statement s2 = conn2.createStatement()) {
                    // Wait until Thread 1 has locked RM-101
                    assertTrue(t1LockedRoom1.await(5, TimeUnit.SECONDS));

                    s2.executeQuery("SELECT id FROM rooms WHERE id = 'RM-102' FOR UPDATE");
                    t2LockedRoom2.countDown();

                    // Now try to lock RM-101 (creates circular dependency: T1 holds 101 wants 102; T2 holds 102 wants 101)
                    s2.executeQuery("SELECT id FROM rooms WHERE id = 'RM-101' FOR UPDATE");
                    conn2.commit();
                }
            } catch (SQLException ex) {
                deadlockException.compareAndSet(null, ex);
            } catch (Exception ex) {
                fail(ex);
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(deadlockException.get(), "PostgreSQL must detect circular deadlock between threads");
        assertEquals("40P01", deadlockException.get().getSQLState(), "SQLSTATE must be 40P01 (deadlock_detected)");
        assertTrue(deadlockException.get().getMessage().toLowerCase().contains("deadlock"),
                "Exception message must identify deadlock");
    }

    @Test
    @Order(8)
    @DisplayName("Part B.7: Deterministic alphabetical lock ordering eliminates deadlocks between concurrent threads")
    void testDeterministicLockOrderingPreventsDeadlocks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        // Both threads request RM-101 and RM-102 in EXACT SAME alphabetical order (RM-101 then RM-102)
        Callable<Void> task = () -> {
            startLatch.await();
            try (Connection conn = createConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    // Standard deterministic order: ORDER BY id ASC
                    stmt.executeQuery("SELECT id FROM rooms WHERE id IN ('RM-101', 'RM-102') ORDER BY id ASC FOR UPDATE");
                    Thread.sleep(50); // Hold lock briefly
                    conn.commit();
                }
            } catch (Exception ex) {
                errorOccurred.set(true);
            }
            return null;
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);

        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertFalse(errorOccurred.get(), "With deterministic alphabetical ordering, no deadlocks occur and both threads succeed sequentially");
    }
}
