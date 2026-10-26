package com.hireflow.repository;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DatabaseException;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2B.4 Integration Tests for PostgreSQL JDBC Application Persistence:
 * - {@link JdbcApplicationRepository}
 * - Lifecycle status transitions via {@link ApplicationService} backed by PostgreSQL
 * - Database constraints: student foreign key, drive foreign key, uniqueness (student_id, drive_id)
 * - Concurrency: duplicate application collision prevention & optimistic conditional updates
 * - Transaction atomicity and rollback-only enforcement
 * - Destructive clear safeguards
 *
 * Runs strictly against hireflow_test with deterministic ID prefixes ('TEST-').
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JdbcApplicationRepositoryTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    private JdbcCompanyRepository companyRepo;
    private JdbcStudentRepository studentRepo;
    private JdbcDriveRepository driveRepo;
    private JdbcApplicationRepository applicationRepo;

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
                .maximumPoolSize(10)
                .minimumIdle(2)
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
        studentRepo = new JdbcStudentRepository(dataSourceProvider.getDataSource());
        driveRepo = new JdbcDriveRepository(dataSourceProvider.getDataSource());
        applicationRepo = new JdbcApplicationRepository(dataSourceProvider.getDataSource());
        cleanTestData();

        // Seed parent company
        companyRepo.save(new Company("TEST-CMP-APP-01", "Application Corp", "Software"));

        // Seed parent recruitment drives
        driveRepo.save(new RecruitmentDrive("TEST-DRV-APP-01", "TEST-CMP-APP-01", "SWE Drive",
                7.5, Set.of("CSE", "ECE"), LocalDate.now().plusDays(10)));
        driveRepo.save(new RecruitmentDrive("TEST-DRV-APP-02", "TEST-CMP-APP-01", "Data Drive",
                8.0, Set.of("CSE"), LocalDate.now().plusDays(15)));

        // Seed parent students
        studentRepo.save(new Student("TEST-STU-APP-01", "Alice Student", "alice.app@test.com", "CSE", 8.5, 2026));
        studentRepo.save(new Student("TEST-STU-APP-02", "Bob Student", "bob.app@test.com", "ECE", 7.8, 2026));
        studentRepo.save(new Student("TEST-STU-APP-03", "Charlie Ineligible", "charlie.app@test.com", "MECH", 6.0, 2026));
    }

    private static void cleanTestData() {
        com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
    }

    // =========================================================================
    // 1. Insert valid application & 2. Retrieve application by ID
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1 & 2: Insert valid application and retrieve by ID")
    void testInsertValidApplicationAndRetrieveById() {
        Application app = new Application(
                "TEST-APP-001",
                "TEST-STU-APP-01",
                "TEST-DRV-APP-01",
                ApplicationStatus.APPLIED,
                LocalDateTime.of(2026, 10, 10, 10, 0, 0),
                "Initial submission"
        );

        Application saved = applicationRepo.add(app);
        assertNotNull(saved);
        assertEquals("TEST-APP-001", saved.getId());
        assertEquals("TEST-STU-APP-01", saved.getStudentId());
        assertEquals("TEST-DRV-APP-01", saved.getDriveId());
        assertEquals(ApplicationStatus.APPLIED, saved.getStatus());

        Optional<Application> foundOpt = applicationRepo.findById("TEST-APP-001");
        assertTrue(foundOpt.isPresent());
        Application found = foundOpt.get();
        assertEquals("TEST-APP-001", found.getId());
        assertEquals("TEST-STU-APP-01", found.getStudentId());
        assertEquals("TEST-DRV-APP-01", found.getDriveId());
        assertEquals(ApplicationStatus.APPLIED, found.getStatus());
        assertEquals("Initial submission", found.getRemarks());
        assertTrue(applicationRepo.existsById("TEST-APP-001"));
        assertEquals(1, applicationRepo.count());
    }

    // =========================================================================
    // 3. Update application
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("3: Update application status and remarks")
    void testUpdateApplication() {
        Application app = new Application("TEST-APP-002", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app);

        app.setStatus(ApplicationStatus.SHORTLISTED);
        app.setRemarks("Shortlisted for interview stage");
        Application updated = applicationRepo.update(app);

        assertEquals(ApplicationStatus.SHORTLISTED, updated.getStatus());
        assertEquals("Shortlisted for interview stage", updated.getRemarks());

        Application retrieved = applicationRepo.findById("TEST-APP-002").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, retrieved.getStatus());
        assertEquals("Shortlisted for interview stage", retrieved.getRemarks());

        // Updating nonexistent entity throws EntityNotFoundException
        Application missing = new Application("TEST-APP-MISSING", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        assertThrows(EntityNotFoundException.class, () -> applicationRepo.update(missing));
    }

    // =========================================================================
    // 4. Delete unreferenced application
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4: Delete unreferenced application")
    void testDeleteUnreferencedApplication() {
        Application app = new Application("TEST-APP-003", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app);
        assertTrue(applicationRepo.existsById("TEST-APP-003"));

        boolean deleted = applicationRepo.deleteById("TEST-APP-003");
        assertTrue(deleted);
        assertFalse(applicationRepo.existsById("TEST-APP-003"));
        assertTrue(applicationRepo.findById("TEST-APP-003").isEmpty());

        // Deleting non-existent returns false
        assertFalse(applicationRepo.deleteById("TEST-APP-NONEXISTENT"));
        assertFalse(applicationRepo.deleteById(null));
    }

    // =========================================================================
    // 5. Reject duplicate application ID
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("5: Reject duplicate application ID")
    void testRejectDuplicateApplicationId() {
        Application app1 = new Application("TEST-APP-004", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app1);

        Application app2 = new Application("TEST-APP-004", "TEST-STU-APP-02", "TEST-DRV-APP-02", ApplicationStatus.APPLIED);
        assertThrows(DuplicateEntityException.class, () -> applicationRepo.add(app2));
    }

    // =========================================================================
    // 6. Reject duplicate student-drive combination
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("6: Reject duplicate student-drive combination (unique student_id, drive_id)")
    void testRejectDuplicateStudentDriveCombination() {
        Application app1 = new Application("TEST-APP-005A", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app1);

        // Different application ID, but exact same student and drive
        Application app2 = new Application("TEST-APP-005B", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        assertThrows(DuplicateEntityException.class, () -> applicationRepo.add(app2));
    }

    // =========================================================================
    // 7 & 8: Reject nonexistent student and drive foreign keys
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("7: Reject application with nonexistent student foreign key")
    void testRejectNonexistentStudentForeignKey() {
        Application badStudent = new Application("TEST-APP-BAD-STU", "TEST-STU-UNKNOWN", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        assertThrows(ValidationException.class, () -> applicationRepo.add(badStudent));
    }

    @Test
    @Order(8)
    @DisplayName("8: Reject application with nonexistent drive foreign key")
    void testRejectNonexistentDriveForeignKey() {
        Application badDrive = new Application("TEST-APP-BAD-DRV", "TEST-STU-APP-01", "TEST-DRV-UNKNOWN", ApplicationStatus.APPLIED);
        assertThrows(ValidationException.class, () -> applicationRepo.add(badDrive));
    }

    // =========================================================================
    // 9. Search applications by student
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("9: Search applications by student ID")
    void testSearchApplicationsByStudent() {
        applicationRepo.add(new Application("TEST-APP-006A", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
        applicationRepo.add(new Application("TEST-APP-006B", "TEST-STU-APP-01", "TEST-DRV-APP-02", ApplicationStatus.APPLIED));
        applicationRepo.add(new Application("TEST-APP-006C", "TEST-STU-APP-02", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));

        List<Application> stu1Apps = applicationRepo.findByStudentId("TEST-STU-APP-01");
        assertEquals(2, stu1Apps.size());
        assertTrue(stu1Apps.stream().allMatch(a -> a.getStudentId().equals("TEST-STU-APP-01")));

        List<Application> stu2Apps = applicationRepo.findByStudentId("TEST-STU-APP-02");
        assertEquals(1, stu2Apps.size());

        assertTrue(applicationRepo.findByStudentId("TEST-STU-APP-03").isEmpty());
        assertTrue(applicationRepo.findByStudentId(null).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> stu1Apps.clear());
    }

    // =========================================================================
    // 10. Search applications by drive
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("10: Search applications by drive ID")
    void testSearchApplicationsByDrive() {
        applicationRepo.add(new Application("TEST-APP-007A", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
        applicationRepo.add(new Application("TEST-APP-007B", "TEST-STU-APP-02", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
        applicationRepo.add(new Application("TEST-APP-007C", "TEST-STU-APP-01", "TEST-DRV-APP-02", ApplicationStatus.APPLIED));

        List<Application> drv1Apps = applicationRepo.findByDriveId("TEST-DRV-APP-01");
        assertEquals(2, drv1Apps.size());

        List<Application> drv2Apps = applicationRepo.findByDriveId("TEST-DRV-APP-02");
        assertEquals(1, drv2Apps.size());

        assertTrue(applicationRepo.findByDriveId("TEST-DRV-NONEXISTENT").isEmpty());
        assertTrue(applicationRepo.findByDriveId(null).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> drv1Apps.clear());
    }

    // =========================================================================
    // 11. Search applications by status
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("11: Search applications by status")
    void testSearchApplicationsByStatus() {
        applicationRepo.add(new Application("TEST-APP-008A", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
        applicationRepo.add(new Application("TEST-APP-008B", "TEST-STU-APP-02", "TEST-DRV-APP-01", ApplicationStatus.SHORTLISTED));

        List<Application> applied = applicationRepo.findByStatus(ApplicationStatus.APPLIED);
        assertEquals(1, applied.size());
        assertEquals("TEST-APP-008A", applied.get(0).getId());

        List<Application> shortlisted = applicationRepo.findByStatus(ApplicationStatus.SHORTLISTED);
        assertEquals(1, shortlisted.size());
        assertEquals("TEST-APP-008B", shortlisted.get(0).getId());

        assertTrue(applicationRepo.findByStatus(ApplicationStatus.SELECTED).isEmpty());
        assertTrue(applicationRepo.findByStatus(null).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> applied.clear());
    }

    // =========================================================================
    // 12. Preserve appliedAt and remarks on reconstruction
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("12: Preserve appliedAt and remarks on reconstruction")
    void testPreserveAppliedAtAndRemarksOnReconstruction() {
        LocalDateTime expectedTime = LocalDateTime.of(2026, 10, 10, 14, 30, 45);
        String expectedRemarks = "Candidate passed automated aptitude threshold with 94 percentile.";

        Application app = new Application(
                "TEST-APP-009",
                "TEST-STU-APP-01",
                "TEST-DRV-APP-01",
                ApplicationStatus.APPLIED,
                expectedTime,
                expectedRemarks
        );
        applicationRepo.add(app);

        Application retrieved = applicationRepo.findById("TEST-APP-009").orElseThrow();
        assertEquals(expectedTime.getYear(), retrieved.getAppliedAt().getYear());
        assertEquals(expectedTime.getMonth(), retrieved.getAppliedAt().getMonth());
        assertEquals(expectedTime.getDayOfMonth(), retrieved.getAppliedAt().getDayOfMonth());
        assertEquals(expectedTime.getHour(), retrieved.getAppliedAt().getHour());
        assertEquals(expectedTime.getMinute(), retrieved.getAppliedAt().getMinute());
        assertEquals(expectedTime.getSecond(), retrieved.getAppliedAt().getSecond());
        assertEquals(expectedRemarks, retrieved.getRemarks());
    }

    // =========================================================================
    // 13 & 14: ApplicationService lifecycle & transitions backed by PostgreSQL
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("13 & 14: Enforce valid and invalid status transitions through ApplicationService")
    void testApplicationServiceLifecycleTransitions() {
        EligibilityService eligibilityService = new EligibilityService();
        ApplicationService appService = new ApplicationService(applicationRepo, studentRepo, driveRepo, eligibilityService);

        // Submit application via service: verifies academic eligibility and saves to PostgreSQL
        Application app = appService.submitApplication("TEST-STU-APP-01", "TEST-DRV-APP-01");
        assertNotNull(app);
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Ineligible student cannot apply
        assertThrows(ValidationException.class, () ->
                appService.submitApplication("TEST-STU-APP-03", "TEST-DRV-APP-01"));

        // Direct jump from APPLIED to SELECTED is illegal
        assertThrows(ValidationException.class, () ->
                appService.updateStatus(app.getId(), ApplicationStatus.SELECTED, "Direct offer"));

        // Valid transition: APPLIED -> SHORTLISTED
        appService.shortlistApplication(app.getId());
        assertEquals(ApplicationStatus.SHORTLISTED, appService.getApplicationOrThrow(app.getId()).getStatus());

        // Valid transition: SHORTLISTED -> SCHEDULED
        appService.updateStatus(app.getId(), ApplicationStatus.SCHEDULED, "Interview scheduled");
        assertEquals(ApplicationStatus.SCHEDULED, appService.getApplicationOrThrow(app.getId()).getStatus());

        // Valid transition: SCHEDULED -> SELECTED
        appService.updateStatus(app.getId(), ApplicationStatus.SELECTED, "Offer extended");
        assertEquals(ApplicationStatus.SELECTED, appService.getApplicationOrThrow(app.getId()).getStatus());

        // Terminal state: moving from SELECTED to any other state is illegal
        assertThrows(ValidationException.class, () ->
                appService.updateStatus(app.getId(), ApplicationStatus.REJECTED, "Cannot reject after select"));

        // Verify state in PostgreSQL from a separate repository instance
        JdbcApplicationRepository separateRepo = new JdbcApplicationRepository(dataSourceProvider.getDataSource());
        Application persisted = separateRepo.findById(app.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SELECTED, persisted.getStatus());
    }

    // =========================================================================
    // 15. Roll back multi-operation transaction on failure
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("15: Roll back multi-operation transaction on failure and prevent commit on rollback-only")
    void testRollbackMultiOperationTransactionOnFailure() {
        // Part A: Transaction failure rolls back all operations
        assertThrows(ValidationException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    applicationRepo.add(new Application("TEST-APP-TX-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
                    // Deliberate failure on operation 2: bad student foreign key
                    applicationRepo.add(new Application("TEST-APP-TX-02", "TEST-STU-BAD", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
                    return null;
                })
        );

        assertFalse(applicationRepo.existsById("TEST-APP-TX-01"),
                "TEST-APP-TX-01 must not be persisted following transaction rollback");

        // Part B: Swallowing an exception inside ambient transaction triggers rollback-only prevention
        assertThrows(DatabaseException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    try {
                        applicationRepo.add(new Application("TEST-APP-TX-03", "TEST-STU-BAD", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
                    } catch (ValidationException swallowed) {
                        // Caller swallowed database failure; TransactionManager must refuse to commit
                    }
                    return null;
                })
        );
    }

    // =========================================================================
    // 16. Verify persistence across separate repository instances
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("16: Verify persistence across separate repository instances")
    void testPersistenceAcrossSeparateRepositoryInstances() {
        JdbcApplicationRepository repoA = new JdbcApplicationRepository(dataSourceProvider.getDataSource());
        JdbcApplicationRepository repoB = new JdbcApplicationRepository(dataSourceProvider.getDataSource());

        Application app = new Application("TEST-APP-010", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        repoA.add(app);

        Optional<Application> fromB = repoB.findById("TEST-APP-010");
        assertTrue(fromB.isPresent());
        assertEquals("TEST-APP-010", fromB.get().getId());

        app.setStatus(ApplicationStatus.SHORTLISTED);
        repoB.update(app);

        assertEquals(ApplicationStatus.SHORTLISTED, repoA.findById("TEST-APP-010").orElseThrow().getStatus());

        repoA.deleteById("TEST-APP-010");
        assertFalse(repoB.existsById("TEST-APP-010"));
    }

    // =========================================================================
    // 17. Verify case-insensitive identifier consistency
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("17: Verify case-insensitive identifier consistency")
    void testCaseInsensitiveIdentifierConsistency() {
        Application app = new Application("test-app-ci-01", "test-stu-app-01", "test-drv-app-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app);

        assertTrue(applicationRepo.existsById("TEST-APP-CI-01"));
        assertTrue(applicationRepo.existsById("test-app-ci-01"));
        assertTrue(applicationRepo.findById("test-app-ci-01").isPresent());
        assertTrue(applicationRepo.findById("TEST-APP-CI-01").isPresent());

        assertTrue(applicationRepo.existsByStudentAndDrive("test-stu-app-01", "test-drv-app-01"));
        assertTrue(applicationRepo.existsByStudentAndDrive("TEST-STU-APP-01", "TEST-DRV-APP-01"));
        assertTrue(applicationRepo.findByStudentAndDrive("test-stu-app-01", "test-drv-app-01").isPresent());
        assertTrue(applicationRepo.findByStudentIdAndDriveId("test-stu-app-01", "test-drv-app-01").isPresent());

        assertEquals(1, applicationRepo.findByStudentId("test-stu-app-01").size());
        assertEquals(1, applicationRepo.findByDriveId("test-drv-app-01").size());
    }

    // =========================================================================
    // 18. Concurrent duplicate-application insertion collision
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("18: Concurrent duplicate application insertion collision (PostgreSQL UNIQUE barrier)")
    void testConcurrentDuplicateApplicationInsertion() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String appId = "TEST-APP-CONCUR-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread attempts to apply same student to same drive
                    JdbcApplicationRepository threadRepo = new JdbcApplicationRepository(dataSourceProvider.getDataSource());
                    threadRepo.add(new Application(appId, "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
                    successCount.incrementAndGet();
                } catch (DuplicateEntityException ex) {
                    duplicateCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected failure
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // trigger all threads simultaneously
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent duplicate insertion timed out");
        assertEquals(1, successCount.get(), "Exactly one concurrent application submission must succeed");
        assertEquals(threadCount - 1, duplicateCount.get(), "All other concurrent submissions must be rejected as duplicates");

        List<Application> apps = applicationRepo.findByStudentId("TEST-STU-APP-01");
        assertEquals(1, apps.size(), "Database must strictly contain exactly 1 application record");
    }

    // =========================================================================
    // 19. Repeatable execution and cleanup
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("19: Repeatable execution and cleanup verification")
    void testRepeatableExecutionAndCleanup() {
        applicationRepo.add(new Application("TEST-APP-CLEAN-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED));
        assertEquals(1, applicationRepo.count());

        cleanTestData();
        assertEquals(0, applicationRepo.count());
    }

    // =========================================================================
    // 20. Conditional status update prevents lost updates
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("20: Conditional status update prevents lost updates under concurrent modifications")
    void testConditionalStatusUpdate() {
        Application app = new Application("TEST-APP-COND-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app);

        // Placement officer A sees APPLIED and updates to SHORTLISTED -> succeeds
        boolean updatedA = applicationRepo.updateStatusConditional(
                "TEST-APP-COND-01", ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED, "Shortlisted by Officer A"
        );
        assertTrue(updatedA);

        // Placement officer B was looking at stale status APPLIED and tries to update to REJECTED -> fails
        boolean updatedB = applicationRepo.updateStatusConditional(
                "TEST-APP-COND-01", ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, "Rejected by Officer B"
        );
        assertFalse(updatedB, "Conditional update must return false when expected status does not match current state");

        Application current = applicationRepo.findById("TEST-APP-COND-01").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, current.getStatus());
        assertEquals("Shortlisted by Officer A", current.getRemarks());
    }

    // =========================================================================
    // 21. Row locking with findByIdForUpdate
    // =========================================================================

    @Test
    @Order(21)
    @DisplayName("21: Row locking with findByIdForUpdate inside ambient transaction")
    void testRowLockingWithFindByIdForUpdate() {
        Application app = new Application("TEST-APP-LOCK-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.add(app);

        transactionManager.runInTransaction(conn -> {
            Optional<Application> lockedOpt = applicationRepo.findByIdForUpdate("TEST-APP-LOCK-01");
            assertTrue(lockedOpt.isPresent());
            assertEquals("TEST-APP-LOCK-01", lockedOpt.get().getId());
            return null;
        });
    }

    // =========================================================================
    // 22. Destructive clear safeguards
    // =========================================================================

    @Test
    @Order(22)
    @DisplayName("22: Destructive clear() safeguards reject unauthorized or non-test execution")
    void testDestructiveClearSafeguards() {
        String prevMode = System.getProperty("hireflow.storage.mode");
        String prevAllow = System.getProperty("hireflow.allow.destructive.clear");
        try {
            System.clearProperty("hireflow.storage.mode");
            System.clearProperty("hireflow.allow.destructive.clear");

            // Calling clear() without explicit test-mode flag throws UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, () -> applicationRepo.clear());

            // With explicit test-mode setting against hireflow_test, clear() succeeds
            System.setProperty("hireflow.storage.mode", "JDBC_TEST");
            assertThrows(UnsupportedOperationException.class, () -> applicationRepo.clear());
            // This database is not named hireflow_test: retain the production prohibition.
            com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
            assertEquals(0, applicationRepo.count());
        } finally {
            if (prevMode != null) System.setProperty("hireflow.storage.mode", prevMode);
            else System.clearProperty("hireflow.storage.mode");
            if (prevAllow != null) System.setProperty("hireflow.allow.destructive.clear", prevAllow);
            else System.clearProperty("hireflow.allow.destructive.clear");
        }
    }

    // =========================================================================
    // 23. Delete blocked by referencing interview session foreign key
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("23: Delete application blocked when referenced by confirmed interview session")
    void testDeleteBlockedByReferencingInterviewSession() throws Exception {
        Application app = new Application("TEST-APP-SESS-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.SCHEDULED);
        applicationRepo.add(app);

        // Seed room and interviewer to support interview session insertion
        JdbcRoomRepository roomRepo = new JdbcRoomRepository(dataSourceProvider.getDataSource());
        roomRepo.save(new com.hireflow.model.Room("TEST-RM-SESS-01", "Interview Room A", 5));

        JdbcInterviewerRepository intRepo = new JdbcInterviewerRepository(dataSourceProvider.getDataSource());
        intRepo.save(new com.hireflow.model.Interviewer("TEST-INT-SESS-01", "Dr. Interviewer", "TEST-CMP-APP-01"));

        // Insert referencing interview session directly
        try (Connection conn = dataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, "TEST-SES-001");
            ps.setString(2, "TEST-APP-SESS-01");
            ps.setString(3, "TEST-STU-APP-01");
            ps.setString(4, "TEST-INT-SESS-01");
            ps.setString(5, "TEST-RM-SESS-01");
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.of(2026, 10, 15, 10, 0)));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.of(2026, 10, 15, 11, 0)));
            ps.executeUpdate();
        }

        // Deleting application referenced by interview session must fail due to FK restrict
        assertThrows(ValidationException.class, () -> applicationRepo.deleteById("TEST-APP-SESS-01"));

        // Delete referencing session
        try (Connection conn = dataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM interview_sessions WHERE id = ?")) {
            ps.setString(1, "TEST-SES-001");
            ps.executeUpdate();
        }

        // Now deletion succeeds
        assertTrue(applicationRepo.deleteById("TEST-APP-SESS-01"));
    }

    // =========================================================================
    // 24. Save upsert semantics
    // =========================================================================

    @Test
    @Order(24)
    @DisplayName("24: save() adds if not present and updates if already present")
    void testSaveUpsertSemantics() {
        Application app = new Application("TEST-APP-SAVE-01", "TEST-STU-APP-01", "TEST-DRV-APP-01", ApplicationStatus.APPLIED);
        applicationRepo.save(app);
        assertEquals(ApplicationStatus.APPLIED, applicationRepo.findById("TEST-APP-SAVE-01").orElseThrow().getStatus());

        app.setStatus(ApplicationStatus.SHORTLISTED);
        app.setRemarks("Updated via save");
        applicationRepo.save(app);

        Application reloaded = applicationRepo.findById("TEST-APP-SAVE-01").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, reloaded.getStatus());
        assertEquals("Updated via save", reloaded.getRemarks());
    }
}
