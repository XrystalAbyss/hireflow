package com.hireflow.repository;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.RecruitmentDrive;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2B.3 Integration Tests for PostgreSQL JDBC Repositories:
 * - {@link JdbcInterviewerRepository}
 * - {@link JdbcDriveRepository}
 *
 * Runs strictly against hireflow_test with deterministic ID prefixes ('TEST-').
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JdbcInterviewerDriveRepositoryTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    private JdbcCompanyRepository companyRepo;
    private JdbcInterviewerRepository interviewerRepo;
    private JdbcDriveRepository driveRepo;

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
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", com.hireflow.testsupport.CoreTestDatabase.url())));
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
        if (dbAvailable && dataSourceProvider != null) {
            cleanTestData();
            dataSourceProvider.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(dbAvailable, "PostgreSQL test database not available at " + (testConfig != null ? testConfig.getJdbcUrl() : ""));
        companyRepo = new JdbcCompanyRepository(dataSourceProvider.getDataSource());
        interviewerRepo = new JdbcInterviewerRepository(dataSourceProvider.getDataSource());
        driveRepo = new JdbcDriveRepository(dataSourceProvider.getDataSource());
        cleanTestData();

        // Seed a dedicated parent test company for foreign-key references
        companyRepo.save(new Company("TEST-CMP-DRV-01", "Drive Test Corp", "Technology"));
        companyRepo.save(new Company("TEST-CMP-DRV-02", "Secondary Test Corp", "Finance"));
    }

    private static void cleanTestData() {
        com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
    }

    // =========================================================================
    // Interviewer Repository Tests
    // =========================================================================

    @Nested
    @DisplayName("Interviewer Repository Operations")
    class InterviewerTests {

        @Test
        @DisplayName("Valid add, findById, existsById, and count")
        void testAddAndFindInterviewer() {
            Interviewer interviewer = new Interviewer("TEST-INT-01", "Dr. Jane Foster", "TEST-CMP-DRV-01");
            Interviewer added = interviewerRepo.add(interviewer);

            assertNotNull(added);
            assertEquals("TEST-INT-01", added.getId());
            assertTrue(interviewerRepo.existsById("TEST-INT-01"));

            Optional<Interviewer> found = interviewerRepo.findById("TEST-INT-01");
            assertTrue(found.isPresent());
            assertEquals("Dr. Jane Foster", found.get().getName());
            assertEquals("TEST-CMP-DRV-01", found.get().getCompanyId());
        }

        @Test
        @DisplayName("Invalid company foreign key throws ValidationException (SQLSTATE 23503)")
        void testInvalidCompanyForeignKeyThrows() {
            Interviewer invalid = new Interviewer("TEST-INT-INV", "No Company Guy", "CMP-NON-EXISTENT");
            ValidationException ex = assertThrows(ValidationException.class, () -> interviewerRepo.add(invalid));
            assertTrue(ex.getMessage().toLowerCase().contains("company") || ex.getMessage().toLowerCase().contains("key")
                    || ex.getMessage().toLowerCase().contains("insert"));
        }

        @Test
        @DisplayName("Duplicate interviewer ID throws DuplicateEntityException (SQLSTATE 23505)")
        void testDuplicateIdThrows() {
            Interviewer i1 = new Interviewer("TEST-INT-DUP", "First Interviewer", "TEST-CMP-DRV-01");
            Interviewer i2 = new Interviewer("TEST-INT-DUP", "Second Interviewer", "TEST-CMP-DRV-01");
            interviewerRepo.add(i1);

            assertThrows(DuplicateEntityException.class, () -> interviewerRepo.add(i2));
        }

        @Test
        @DisplayName("Update interviewer modifies name, company, and maintains updated_at")
        void testUpdateInterviewer() {
            Interviewer interviewer = new Interviewer("TEST-INT-02", "Agent Smith", "TEST-CMP-DRV-01");
            interviewerRepo.add(interviewer);

            interviewer.setName("Agent Smithers");
            interviewer.setCompanyId("TEST-CMP-DRV-02");
            Interviewer updated = interviewerRepo.update(interviewer);

            assertEquals("Agent Smithers", updated.getName());
            assertEquals("TEST-CMP-DRV-02", updated.getCompanyId());

            Interviewer fromDb = interviewerRepo.findById("TEST-INT-02").orElseThrow();
            assertEquals("Agent Smithers", fromDb.getName());
            assertEquals("TEST-CMP-DRV-02", fromDb.getCompanyId());
        }

        @Test
        @DisplayName("Update non-existent interviewer throws EntityNotFoundException")
        void testUpdateNonExistentThrows() {
            Interviewer ghost = new Interviewer("TEST-INT-99", "Phantom", "TEST-CMP-DRV-01");
            assertThrows(EntityNotFoundException.class, () -> interviewerRepo.update(ghost));
        }

        @Test
        @DisplayName("FindByCompanyId returns matching interviewers case-insensitively")
        void testFindByCompanyId() {
            interviewerRepo.add(new Interviewer("TEST-INT-C1", "Lead Alpha", "TEST-CMP-DRV-01"));
            interviewerRepo.add(new Interviewer("TEST-INT-C2", "Lead Beta", "TEST-CMP-DRV-01"));
            interviewerRepo.add(new Interviewer("TEST-INT-C3", "Lead Gamma", "TEST-CMP-DRV-02"));

            List<Interviewer> corp1 = interviewerRepo.findByCompanyId("test-cmp-drv-01");
            assertEquals(2, corp1.size());
            assertEquals("TEST-INT-C1", corp1.get(0).getId());
            assertEquals("TEST-INT-C2", corp1.get(1).getId());

            assertThrows(UnsupportedOperationException.class, () -> corp1.clear());
        }

        @Test
        @DisplayName("DeleteById removes interviewer")
        void testDeleteInterviewer() {
            Interviewer i = new Interviewer("TEST-INT-DEL", "Delete Me", "TEST-CMP-DRV-01");
            interviewerRepo.add(i);

            assertTrue(interviewerRepo.deleteById("TEST-INT-DEL"));
            assertFalse(interviewerRepo.deleteById("TEST-INT-DEL"));
            assertFalse(interviewerRepo.existsById("TEST-INT-DEL"));
        }

        @Test
        @DisplayName("Case-insensitive ID lookup finds interviewer")
        void testCaseInsensitiveIdLookup() {
            interviewerRepo.add(new Interviewer("TEST-INT-CASE", "Case Test", "TEST-CMP-DRV-01"));

            Optional<Interviewer> found = interviewerRepo.findById("test-int-case");
            assertTrue(found.isPresent());
            assertEquals("TEST-INT-CASE", found.get().getId());
        }

        @Test
        @DisplayName("Save upsert behavior inserts then updates")
        void testSaveInterviewer() {
            Interviewer i = new Interviewer("TEST-INT-SAVE", "Save Interviewer", "TEST-CMP-DRV-01");
            interviewerRepo.save(i);

            assertEquals("Save Interviewer", interviewerRepo.findById("TEST-INT-SAVE").orElseThrow().getName());

            i.setName("Updated Save Interviewer");
            interviewerRepo.save(i);

            assertEquals("Updated Save Interviewer", interviewerRepo.findById("TEST-INT-SAVE").orElseThrow().getName());
        }

        @Test
        @DisplayName("Persistence across independent repository instances")
        void testPersistenceAcrossInstances() {
            interviewerRepo.add(new Interviewer("TEST-INT-PERSIST", "Persistent Interviewer", "TEST-CMP-DRV-01"));

            JdbcInterviewerRepository freshRepo = new JdbcInterviewerRepository(dataSourceProvider.getDataSource());
            Optional<Interviewer> found = freshRepo.findById("TEST-INT-PERSIST");
            assertTrue(found.isPresent());
            assertEquals("Persistent Interviewer", found.get().getName());
        }
    }

    // =========================================================================
    // Recruitment Drive Repository Tests
    // =========================================================================

    @Nested
    @DisplayName("Recruitment Drive Repository Operations")
    class DriveTests {

        @Test
        @DisplayName("Drive creation with multiple eligible branches reconstructs complete branch set")
        void testAddAndFindDriveWithBranches() {
            Set<String> branches = Set.of("CSE", "IT", "ECE");
            RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-01", "TEST-CMP-DRV-01",
                    "Software Engineer", 8.25, branches, LocalDate.now().plusDays(14));

            RecruitmentDrive added = driveRepo.add(drive);
            assertNotNull(added);
            assertEquals("TEST-DRV-01", added.getId());

            Optional<RecruitmentDrive> found = driveRepo.findById("TEST-DRV-01");
            assertTrue(found.isPresent());
            RecruitmentDrive retrieved = found.get();
            assertEquals("Software Engineer", retrieved.getJobTitle());
            assertEquals(8.25, retrieved.getMinimumCgpa());
            assertEquals("TEST-CMP-DRV-01", retrieved.getCompanyId());
            assertEquals(3, retrieved.getEligibleBranches().size());
            assertTrue(retrieved.getEligibleBranches().containsAll(Set.of("CSE", "IT", "ECE")));
        }

        @Test
        @DisplayName("Drive creation with empty eligible branches succeeds")
        void testAddDriveWithEmptyBranches() {
            RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-EMPTY", "TEST-CMP-DRV-01",
                    "Open Intern", 7.0, Collections.emptySet(), LocalDate.now().plusDays(7));

            driveRepo.add(drive);
            RecruitmentDrive retrieved = driveRepo.findById("TEST-DRV-EMPTY").orElseThrow();
            assertTrue(retrieved.getEligibleBranches().isEmpty());
            assertTrue(retrieved.isBranchEligible("ANY_BRANCH"));
        }

        @Test
        @DisplayName("Invalid company foreign key throws ValidationException (SQLSTATE 23503)")
        void testInvalidCompanyForeignKeyThrows() {
            RecruitmentDrive invalid = new RecruitmentDrive("TEST-DRV-INV", "CMP-NON-EXISTENT",
                    "QA Engineer", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10));

            assertThrows(ValidationException.class, () -> driveRepo.add(invalid));
        }

        @Test
        @DisplayName("Duplicate drive ID throws DuplicateEntityException (SQLSTATE 23505)")
        void testDuplicateDriveIdThrows() {
            RecruitmentDrive d1 = new RecruitmentDrive("TEST-DRV-DUP", "TEST-CMP-DRV-01",
                    "Title 1", 7.5, Set.of("CSE"), LocalDate.now().plusDays(10));
            RecruitmentDrive d2 = new RecruitmentDrive("TEST-DRV-DUP", "TEST-CMP-DRV-01",
                    "Title 2", 8.0, Set.of("IT"), LocalDate.now().plusDays(10));

            driveRepo.add(d1);
            assertThrows(DuplicateEntityException.class, () -> driveRepo.add(d2));
        }

        @Test
        @DisplayName("Drive update synchronizes branch associations (adds and removes branches)")
        void testUpdateDriveSynchronizesBranches() {
            RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-SYNC", "TEST-CMP-DRV-01",
                    "Initial Role", 8.0, Set.of("CSE", "IT"), LocalDate.now().plusDays(10));
            driveRepo.add(drive);

            // Change branches to ECE and MECH (removes CSE, IT; adds ECE, MECH)
            drive.setJobTitle("Lead Systems Engineer");
            drive.setEligibleBranches(Set.of("ECE", "MECH"));
            driveRepo.update(drive);

            RecruitmentDrive updated = driveRepo.findById("TEST-DRV-SYNC").orElseThrow();
            assertEquals("Lead Systems Engineer", updated.getJobTitle());
            assertEquals(2, updated.getEligibleBranches().size());
            assertTrue(updated.getEligibleBranches().containsAll(Set.of("ECE", "MECH")));
            assertFalse(updated.getEligibleBranches().contains("CSE"));
            assertFalse(updated.getEligibleBranches().contains("IT"));
        }

        @Test
        @DisplayName("Update non-existent drive throws EntityNotFoundException")
        void testUpdateNonExistentDriveThrows() {
            RecruitmentDrive ghost = new RecruitmentDrive("TEST-DRV-99", "TEST-CMP-DRV-01",
                    "Ghost Role", 7.0, Set.of("CSE"), LocalDate.now().plusDays(5));
            assertThrows(EntityNotFoundException.class, () -> driveRepo.update(ghost));
        }

        @Test
        @DisplayName("FindByCompanyId returns company drives and reconstructs branches in single query")
        void testFindByCompanyId() {
            driveRepo.add(new RecruitmentDrive("TEST-DRV-C1", "TEST-CMP-DRV-01", "Role 1", 7.0, Set.of("CSE"), LocalDate.now().plusDays(5)));
            driveRepo.add(new RecruitmentDrive("TEST-DRV-C2", "TEST-CMP-DRV-01", "Role 2", 8.0, Set.of("IT", "ECE"), LocalDate.now().plusDays(10)));
            driveRepo.add(new RecruitmentDrive("TEST-DRV-C3", "TEST-CMP-DRV-02", "Role 3", 6.5, Set.of("MECH"), LocalDate.now().plusDays(15)));

            List<RecruitmentDrive> corp1Drives = driveRepo.findByCompanyId("test-cmp-drv-01");
            assertEquals(2, corp1Drives.size());
            assertEquals("TEST-DRV-C1", corp1Drives.get(0).getId());
            assertEquals(1, corp1Drives.get(0).getEligibleBranches().size());
            assertEquals("TEST-DRV-C2", corp1Drives.get(1).getId());
            assertEquals(2, corp1Drives.get(1).getEligibleBranches().size());
        }

        @Test
        @DisplayName("FindActiveDrives returns only drives with deadline >= asOfDate")
        void testFindActiveDrives() {
            LocalDate today = LocalDate.now();
            driveRepo.add(new RecruitmentDrive("TEST-DRV-ACT1", "TEST-CMP-DRV-01", "Active 1", 7.0, Set.of("CSE"), today.plusDays(5)));
            driveRepo.add(new RecruitmentDrive("TEST-DRV-ACT2", "TEST-CMP-DRV-01", "Active Today", 7.5, Set.of("IT"), today));
            driveRepo.add(new RecruitmentDrive("TEST-DRV-PAST", "TEST-CMP-DRV-01", "Expired", 8.0, Set.of("ECE"), today.minusDays(1)));

            List<RecruitmentDrive> active = driveRepo.findActiveDrives(today);
            assertTrue(active.stream().anyMatch(d -> d.getId().equals("TEST-DRV-ACT1")));
            assertTrue(active.stream().anyMatch(d -> d.getId().equals("TEST-DRV-ACT2")));
            assertFalse(active.stream().anyMatch(d -> d.getId().equals("TEST-DRV-PAST")));
        }

        @Test
        @DisplayName("Drive deletion blocked by existing applications (SQLSTATE 23503 ON DELETE RESTRICT)")
        void testDriveDeletionBlockedByApplications() throws Exception {
            String driveId = "TEST-DRV-APP-RESTRICT";
            driveRepo.add(new RecruitmentDrive(driveId, "TEST-CMP-DRV-01", "Software Dev", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)));

            // Insert a student and application referencing this drive
            try (Connection conn = dataSourceProvider.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO students (id, name, email, branch, cgpa, graduation_year) " +
                        "VALUES ('TEST-STU-DRV-01', 'Test App Student', 'app.stu@univ.edu', 'CSE', 8.5, 2026) ON CONFLICT DO NOTHING");
                stmt.executeUpdate("INSERT INTO applications (id, student_id, drive_id, status) " +
                        "VALUES ('TEST-APP-DRV-01', 'TEST-STU-DRV-01', '" + driveId + "', 'APPLIED')");
            }

            // Attempting to delete the drive must fail due to foreign key restriction
            ValidationException ex = assertThrows(ValidationException.class, () -> driveRepo.deleteById(driveId));
            assertTrue(ex.getMessage().toLowerCase().contains("foreign") || ex.getMessage().toLowerCase().contains("violation")
                    || ex.getMessage().toLowerCase().contains("applications") || ex.getMessage().toLowerCase().contains("delete"));

            // Remove application, then deletion succeeds and cascades to branches
            try (Connection conn = dataSourceProvider.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM applications WHERE id = 'TEST-APP-DRV-01'");
            }

            assertTrue(driveRepo.deleteById(driveId), "Drive deletion must succeed once applications are removed");
            assertFalse(driveRepo.existsById(driveId));
        }

        @Test
        @DisplayName("Safe transaction participation does not trigger nested-transaction rejection")
        void testSafeTransactionParticipation() {
            transactionManager.runInTransactionWithoutResult(conn -> {
                RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-TX", "TEST-CMP-DRV-01",
                        "Tx Engineer", 8.5, Set.of("CSE", "IT"), LocalDate.now().plusDays(20));
                // Must participate in the ambient transaction without throwing IllegalStateException
                driveRepo.add(drive);
                assertTrue(driveRepo.existsById("TEST-DRV-TX"));
            });

            // Verify drive persisted after commit
            assertTrue(driveRepo.findById("TEST-DRV-TX").isPresent());
        }

        @Test
        @DisplayName("Transaction rollback upon failed drive operation leaves zero orphaned parent rows")
        void testTransactionRollbackOnFailedDriveOperation() {
            assertThrows(RuntimeException.class, () -> {
                transactionManager.runInTransactionWithoutResult(conn -> {
                    RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-FAIL", "TEST-CMP-DRV-01",
                            "Fail Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
                    driveRepo.add(drive);
                    // Force transactional rollback
                    throw new RuntimeException("Simulated abort after drive insertion");
                });
            });

            assertFalse(driveRepo.existsById("TEST-DRV-FAIL"), "Parent drive must not exist after rollback");
        }

        @Test
        @DisplayName("Save upsert behavior inserts then updates drive and branches")
        void testSaveDrive() {
            RecruitmentDrive d = new RecruitmentDrive("TEST-DRV-SAVE", "TEST-CMP-DRV-01",
                    "Role A", 7.5, Set.of("CSE"), LocalDate.now().plusDays(5));
            driveRepo.save(d);

            assertEquals("Role A", driveRepo.findById("TEST-DRV-SAVE").orElseThrow().getJobTitle());

            d.setJobTitle("Role B");
            d.setEligibleBranches(Set.of("CSE", "ECE"));
            driveRepo.save(d);

            RecruitmentDrive updated = driveRepo.findById("TEST-DRV-SAVE").orElseThrow();
            assertEquals("Role B", updated.getJobTitle());
            assertEquals(2, updated.getEligibleBranches().size());
        }

        @Test
        @DisplayName("Drive manual closure persists is_closed flag and preserves original deadline")
        void testDriveManualClosurePersistenceAndActiveFiltering() {
            LocalDate deadline = LocalDate.now().plusDays(10);
            RecruitmentDrive drive = new RecruitmentDrive("TEST-DRV-CLOSE-01", "TEST-CMP-DRV-01",
                    "Cloud Specialist", 8.0, Set.of("CSE"), deadline, false);
            driveRepo.add(drive);

            RecruitmentDrive found = driveRepo.findById("TEST-DRV-CLOSE-01").orElseThrow();
            assertFalse(found.isClosed(), "New drive must not be closed");
            assertEquals(deadline, found.getApplicationDeadline());

            // Active drives includes it
            List<RecruitmentDrive> activeBefore = driveRepo.findActiveDrives(LocalDate.now());
            assertTrue(activeBefore.stream().anyMatch(d -> "TEST-DRV-CLOSE-01".equals(d.getId())));

            // Manually close the drive
            found.setClosed(true);
            driveRepo.save(found);

            RecruitmentDrive updated = driveRepo.findById("TEST-DRV-CLOSE-01").orElseThrow();
            assertTrue(updated.isClosed(), "Drive must be marked closed in database");
            assertEquals(deadline, updated.getApplicationDeadline(), "Original deadline must be preserved");

            // Active drives query must now exclude the closed drive
            List<RecruitmentDrive> activeAfter = driveRepo.findActiveDrives(LocalDate.now());
            assertFalse(activeAfter.stream().anyMatch(d -> "TEST-DRV-CLOSE-01".equals(d.getId())),
                    "findActiveDrives must exclude manually closed drives");

            driveRepo.deleteById("TEST-DRV-CLOSE-01");
        }
    }

    // =========================================================================
    // Safety & Destructive Operation Guard Tests
    // =========================================================================

    @Nested
    @DisplayName("Safety and Boundary Safeguards")
    class SafetySafeguardsTests {

        @Test
        @DisplayName("Destructive clear() rejects execution against non-test database")
        void testClearRejectionAgainstNonTestDatabase() {
            // Instantiate repository with custom config pointing to non-test URL
            DatabaseConfig devConfig = DatabaseConfig.builder()
                    .mode(DatabaseMode.JDBC_DEV)
                    .jdbcUrl(com.hireflow.testsupport.CoreTestDatabase.url())
                    .username("hireflow_cap8_app")
                    .password(testConfig.getPassword())
                    .build();

            try (DataSourceProvider devProvider = new DataSourceProvider(devConfig)) {
                JdbcCompanyRepository devCompanyRepo = new JdbcCompanyRepository(devProvider.getDataSource());
                UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, devCompanyRepo::clear);
                assertTrue(ex.getMessage().contains("strictly prohibited against non-test databases"));
            } catch (Exception ex) {
                // If dev database is unreachable, verify that clear check failed safely
                assertTrue(ex instanceof UnsupportedOperationException || ex.getMessage().contains("Failed to initialize"));
            }
        }
    }
}
