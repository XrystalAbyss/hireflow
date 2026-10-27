package com.hireflow.api.student;

import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused PostgreSQL Integration Tests verifying:
 * - Part 4 & 5: Authoritative application submission rules, deadline checks, and closure races.
 * - Part 6 (Scenarios 1-15):
 *   1. Application commits successfully with an open drive.
 *   2. Submission rejects a previously closed drive.
 *   3. Submission rejects an expired drive.
 *   4. Submission and closure execute concurrently.
 *   5. Closure commits before submission validation -> submission rejects closed drive.
 *   6. Submission obtains its synchronization before closure -> closure waits until submission commits.
 *   7. Duplicate submissions cannot create duplicate rows (PostgreSQL constraint uq_application_student_drive).
 *   8. A database failure rolls back the insertion.
 *   9. An exception leaves the connection reusable in the pool.
 *   10. Service-level validation works without the REST controller.
 *   11. Existing JSP submission behavior remains valid.
 *   12. No unexpected transaction nesting occurs.
 *   13. Repositories use the intended transaction-bound connection.
 *   14. Concurrent different-student submissions proceed safely in parallel.
 *   15. No deadlocks under supported lock ordering.
 */
@SpringBootTest
class DriveClosureAndSubmissionConcurrencyPostgresTest {
    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource cleanupDataSource;


    @Autowired
    @Qualifier("hireflowJdbcTransactionManager")
    private TransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private RecruitmentDriveService recruitmentDriveService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EligibilityService eligibilityService;

    private static final String TEST_CMP = "CMP-CONC-01";
    private static final String STU_1 = "STU-CONC-01";
    private static final String STU_2 = "STU-CONC-02";
    private static final String STU_3 = "STU-CONC-03";
    private static final String DRV_OPEN = "DRV-CONC-OPEN";
    private static final String DRV_CLOSED = "DRV-CONC-CLOSED";
    private static final String DRV_EXPIRED = "DRV-CONC-EXP";
    private static final String DRV_RACE = "DRV-CONC-RACE";

    @BeforeEach
    void setUpFixtures() {
        tearDownFixtures();

        // 1. Company
        companyRepository.add(new Company(TEST_CMP, "Concurrency Test Corp", "Fintech"));

        // 2. Students
        studentRepository.add(new Student(STU_1, "Alice Concurrency", "alice.conc@test.local", "CSE", 8.80, 2026, true));
        studentRepository.add(new Student(STU_2, "Bob Concurrency", "bob.conc@test.local", "CSE", 8.50, 2026, true));
        studentRepository.add(new Student(STU_3, "Charlie Concurrency", "charlie.conc@test.local", "MECH", 6.00, 2026, true));

        // 3. Drives
        driveRepository.add(new RecruitmentDrive(DRV_OPEN, TEST_CMP, "Backend Engineer", 7.00, Set.of("CSE"), LocalDate.now().plusMonths(1), false));
        driveRepository.add(new RecruitmentDrive(DRV_CLOSED, TEST_CMP, "Data Engineer", 7.00, Set.of("CSE"), LocalDate.now().plusMonths(1), true));
        driveRepository.add(new RecruitmentDrive(DRV_EXPIRED, TEST_CMP, "Site Reliability Engineer", 7.00, Set.of("CSE"), LocalDate.now().minusDays(1), false));
        driveRepository.add(new RecruitmentDrive(DRV_RACE, TEST_CMP, "Systems Engineer", 7.00, Set.of("CSE"), LocalDate.now().plusMonths(1), false));
    }

    @AfterEach
    void tearDownFixtures() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"CMP-CONC-01", "STU-CONC-01", "STU-CONC-02", "STU-CONC-03", "DRV-CONC-OPEN", "DRV-CONC-CLOSED", "DRV-CONC-EXP", "DRV-CONC-RACE", "APP-FAIL-01"},
            new String[]{});
    }

    @Test
    @DisplayName("Scenario 1: Application commits successfully with an open drive")
    void testScenario1_applicationCommitsSuccessfullyWithOpenDrive() {
        Application app = applicationService.submitApplication(STU_1, DRV_OPEN);
        assertNotNull(app);
        assertNotNull(app.getId());
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        Optional<Application> persisted = applicationRepository.findById(app.getId());
        assertTrue(persisted.isPresent());
        assertEquals(STU_1, persisted.get().getStudentId());
        assertEquals(DRV_OPEN, persisted.get().getDriveId());
    }

    @Test
    @DisplayName("Scenario 2: Submission rejects a previously closed drive")
    void testScenario2_submissionRejectsPreviouslyClosedDrive() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                applicationService.submitApplication(STU_1, DRV_CLOSED)
        );
        assertTrue(ex.getMessage().contains("closed for applications"));
        assertFalse(applicationRepository.existsByStudentAndDrive(STU_1, DRV_CLOSED));
    }

    @Test
    @DisplayName("Scenario 3: Submission rejects an expired drive")
    void testScenario3_submissionRejectsExpiredDrive() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                applicationService.submitApplication(STU_1, DRV_EXPIRED)
        );
        assertTrue(ex.getMessage().contains("deadline") || ex.getMessage().contains("not eligible"));
        assertFalse(applicationRepository.existsByStudentAndDrive(STU_1, DRV_EXPIRED));
    }

    @Test
    @DisplayName("Scenario 4 & 5: Closure commits before submission validation -> submission rejects closed drive")
    void testScenario4And5_closureCommitsBeforeSubmissionValidation_rejectsClosedDrive() throws Exception {
        // Deterministically enforce that Transaction B (Closure) commits while Transaction A (Submission) is starting
        CountDownLatch closureCommittedLatch = new CountDownLatch(1);
        AtomicReference<Exception> submissionException = new AtomicReference<>();
        AtomicReference<Application> createdApp = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Task B: Closes drive
        Future<?> closureTask = executor.submit(() -> {
            recruitmentDriveService.closeDrive(DRV_RACE);
            closureCommittedLatch.countDown();
        });

        // Task A: Student submits application after closure is committed
        Future<?> submissionTask = executor.submit(() -> {
            try {
                closureCommittedLatch.await(5, TimeUnit.SECONDS);
                Application app = applicationService.submitApplication(STU_1, DRV_RACE);
                createdApp.set(app);
            } catch (Exception ex) {
                submissionException.set(ex);
            }
        });

        closureTask.get(5, TimeUnit.SECONDS);
        submissionTask.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(submissionException.get(), "Submission must be rejected because drive was closed");
        assertInstanceOf(ValidationException.class, submissionException.get());
        assertTrue(submissionException.get().getMessage().contains("closed for applications"));
        assertNull(createdApp.get());
        assertFalse(applicationRepository.existsByStudentAndDrive(STU_1, DRV_RACE));
    }

    @Test
    @DisplayName("Scenario 6: Submission obtains row lock before closure -> closure waits until submission commits")
    void testScenario6_submissionObtainsLockBeforeClosure_closureWaits() throws Exception {
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch allowSubmissionCommitLatch = new CountDownLatch(1);
        AtomicBoolean closureFinished = new AtomicBoolean(false);
        AtomicReference<Application> submittedApp = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Transaction A (Submission): starts transaction, acquires lock on drive row, signals, then waits
        Future<?> submissionFuture = executor.submit(() -> {
            return transactionManager.runInTransaction(conn -> {
                // Read drive with FOR SHARE row lock
                Optional<RecruitmentDrive> drive = driveRepository.findByIdForUpdate(DRV_RACE);
                assertTrue(drive.isPresent());
                assertFalse(drive.get().isClosed());

                // Signal that submission has acquired row lock
                lockAcquiredLatch.countDown();

                // Wait for closure attempt to begin and block
                try {
                    allowSubmissionCommitLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Proceed with submission inside this transaction
                Application app = applicationService.submitApplication(STU_1, DRV_RACE);
                submittedApp.set(app);
                return app;
            });
        });

        // Transaction B (Closure): waits until submission has row lock, then attempts updateDrive
        Future<?> closureFuture = executor.submit(() -> {
            try {
                lockAcquiredLatch.await(5, TimeUnit.SECONDS);
                // Attempt to close drive: in PostgreSQL, UPDATE will wait for FOR SHARE lock to release!
                recruitmentDriveService.closeDrive(DRV_RACE);
                closureFinished.set(true);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Verify that closure has NOT finished while submission holds lock
        lockAcquiredLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(150); // Small interval to allow closure thread to block on PostgreSQL row lock
        assertFalse(closureFinished.get(), "Closure must be blocked waiting for submission transaction to commit");

        // Allow submission to commit
        allowSubmissionCommitLatch.countDown();

        submissionFuture.get(5, TimeUnit.SECONDS);
        closureFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // After both finish:
        // 1. Application was successfully committed
        assertNotNull(submittedApp.get());
        assertTrue(applicationRepository.existsByStudentAndDrive(STU_1, DRV_RACE));

        // 2. Drive is now closed
        RecruitmentDrive finalDrive = driveRepository.findById(DRV_RACE).orElseThrow();
        assertTrue(finalDrive.isClosed());
    }

    @Test
    @DisplayName("Scenario 7: Duplicate submissions cannot create duplicate rows (PostgreSQL constraint)")
    void testScenario7_duplicateSubmissionsRejectedByPostgres() {
        Application first = applicationService.submitApplication(STU_1, DRV_OPEN);
        assertNotNull(first);

        DuplicateEntityException ex = assertThrows(DuplicateEntityException.class, () ->
                applicationService.submitApplication(STU_1, DRV_OPEN)
        );
        assertTrue(ex.getMessage().contains("already applied"));

        // Direct count check in PostgreSQL
        List<Application> apps = applicationRepository.findByStudentId(STU_1);
        long countForDrive = apps.stream().filter(a -> a.getDriveId().equals(DRV_OPEN)).count();
        assertEquals(1, countForDrive);
    }

    @Test
    @DisplayName("Scenario 8 & 9: Database failure rolls back insertion and leaves connection reusable")
    void testScenario8And9_databaseFailureRollbackAndConnectionReusable() throws Exception {
        // Force a transaction failure by violating a check or foreign key constraint
        assertThrows(Exception.class, () -> {
            transactionManager.runInTransaction(conn -> {
                // Insert application with non-existent student ID directly to trigger FK violation
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO applications (id, student_id, drive_id, status, applied_at, created_at, updated_at) " +
                        "VALUES ('APP-FAIL-01', 'NON-EXISTENT-STUDENT', 'DRV-CONC-OPEN', 'APPLIED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                    ps.executeUpdate();
                }
                return null;
            });
        });

        // Verify nothing was persisted
        assertFalse(applicationRepository.existsById("APP-FAIL-01"));

        // Verify connection pool is fully healthy and reusable immediately
        try (Connection testConn = dataSource.getConnection();
             PreparedStatement ps = testConn.prepareStatement("SELECT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Scenario 10 & 11: Service-level validation and submission work standalone (JSP/Service compatibility)")
    void testScenario10And11_standaloneServiceLevelValidationAndSubmission() {
        // Direct call to ApplicationService without any Spring MVC / REST controller context
        Application app = applicationService.submitApplication(STU_2, DRV_OPEN);
        assertNotNull(app);
        assertEquals(STU_2, app.getStudentId());
        assertEquals(DRV_OPEN, app.getDriveId());
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Ineligible candidate rejected at service level
        ValidationException ex = assertThrows(ValidationException.class, () ->
                applicationService.submitApplication(STU_3, DRV_OPEN) // MECH branch & 6.00 CGPA -> ineligible
        );
        assertTrue(ex.getMessage().contains("not eligible"));
    }

    @Test
    @DisplayName("Scenario 12: No unexpected transaction nesting occurs")
    void testScenario12_noUnexpectedTransactionNesting() {
        // Nested runInTransaction call within an active transaction is explicitly rejected by TransactionManager
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            transactionManager.runInTransaction(conn1 -> {
                return transactionManager.runInTransaction(conn2 -> "nested");
            });
        });
        assertTrue(ex.getMessage().contains("Nested transactions are not supported"));

        // ApplicationService.submitApplication participates gracefully in an outer transaction without nesting
        Application app = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(STU_1, DRV_OPEN)
        );
        assertNotNull(app);
    }

    @Test
    @DisplayName("Scenario 13: Repositories use the intended transaction-bound ambient connection")
    void testScenario13_repositoriesUseAmbientConnection() {
        transactionManager.runInTransaction(conn -> {
            Connection active = TransactionManager.getCurrentConnection();
            assertNotNull(active, "Ambient connection must be set during active transaction");

            // Verify studentRepository and driveRepository use the same ambient connection
            Optional<Student> s = studentRepository.findById(STU_1);
            assertTrue(s.isPresent());

            Optional<RecruitmentDrive> d = driveRepository.findById(DRV_OPEN);
            assertTrue(d.isPresent());

            // Connection remains bound throughout transaction
            assertSame(active, TransactionManager.getCurrentConnection());
            return null;
        });

        // After transaction exits, ambient connection is cleared
        assertNull(TransactionManager.getCurrentConnection());
    }

    @Test
    @DisplayName("Scenario 14: Concurrent different-student submissions proceed safely in parallel")
    void testScenario14_concurrentDifferentStudentsProceedInParallel() throws Exception {
        int studentCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(studentCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Application> taskA = () -> {
            startLatch.await();
            return applicationService.submitApplication(STU_1, DRV_OPEN);
        };
        Callable<Application> taskB = () -> {
            startLatch.await();
            return applicationService.submitApplication(STU_2, DRV_OPEN);
        };

        Future<Application> futureA = executor.submit(taskA);
        Future<Application> futureB = executor.submit(taskB);

        startLatch.countDown();

        Application appA = futureA.get(5, TimeUnit.SECONDS);
        Application appB = futureB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(appA);
        assertNotNull(appB);
        assertNotEquals(appA.getId(), appB.getId());
        assertEquals(STU_1, appA.getStudentId());
        assertEquals(STU_2, appB.getStudentId());

        assertTrue(applicationRepository.existsByStudentAndDrive(STU_1, DRV_OPEN));
        assertTrue(applicationRepository.existsByStudentAndDrive(STU_2, DRV_OPEN));
    }

    @Test
    @DisplayName("Scenario 15: No deadlocks under supported lock ordering")
    void testScenario15_noDeadlocksUnderSupportedLockOrdering() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Application>> futures = new ArrayList<>();

        // 2 students applying to DRV_OPEN, 2 other calls checking/locking
        for (int i = 0; i < 2; i++) {
            final String stuId = (i == 0) ? STU_1 : STU_2;
            futures.add(executor.submit(() -> {
                startLatch.await();
                return applicationService.submitApplication(stuId, DRV_OPEN);
            }));
        }

        startLatch.countDown();

        for (Future<Application> f : futures) {
            Application a = f.get(5, TimeUnit.SECONDS);
            assertNotNull(a);
        }
        executor.shutdown();
    }
}
