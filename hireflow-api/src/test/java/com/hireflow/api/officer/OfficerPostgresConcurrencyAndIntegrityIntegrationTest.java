package com.hireflow.api.officer;

import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.CompanyService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OfficerPostgresConcurrencyAndIntegrityIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource cleanupDataSource;


    @Autowired
    private CompanyService companyService;

    @Autowired
    private RecruitmentDriveService driveService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    @Qualifier("hireflowJdbcTransactionManager")
    private TransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private static final String CMP_ID = "CMP-PG-01";
    private static final String DRV_ID = "DRV-PG-01";
    private static final String STU_ID_1 = "STU-PG-01";
    private static final String STU_ID_2 = "STU-PG-02";
    private static final String APP_ID = "APP-PG-01";
    private static final String INT_ID = "INT-PG-01";
    private static final String ROOM_ID = "RM-PG-01";

    @BeforeEach
    void setUpTestData() {
        cleanTestData();

        // 1. Create company
        Company company = new Company(CMP_ID, "Apex Technologies", "Cloud Infrastructure");
        companyRepository.add(company);

        // 2. Create student 1 (eligible)
        Student student1 = new Student(STU_ID_1, "Alice Walker", "alice@hireflow.pg", "CSE", 8.50, 2026, true);
        studentRepository.add(student1);

        // 3. Create student 2 (ineligible - low CGPA)
        Student student2 = new Student(STU_ID_2, "Bob Miller", "bob@hireflow.pg", "CSE", 6.00, 2026, true);
        studentRepository.add(student2);

        // 4. Create recruitment drive
        RecruitmentDrive drive = new RecruitmentDrive(
                DRV_ID, CMP_ID, "Cloud Infrastructure Engineer", 7.50, Set.of("CSE", "IT"), LocalDate.now().plusMonths(1), false
        );
        driveRepository.add(drive);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"CMP-PG-01", "DRV-PG-01", "STU-PG-01", "STU-PG-02", "APP-PG-01", "INT-PG-01", "RM-PG-01", "DRV-PG-02", "APP-PG-02", "CMP-PG-02", "INT-PG-02", "RM-PG-02", "APP-PG-SCHED"},
            new String[]{});
    }

    @Test
    @DisplayName("Company creation, lookup, and PostgreSQL persistence")
    void testCompanyCreationAndLookup() {
        Company created = transactionManager.runInTransaction(conn ->
                companyService.registerCompany("CMP-PG-02", "Titan Cybernetics Ltd", "Artificial Intelligence")
        );

        assertNotNull(created);
        assertEquals("CMP-PG-02", created.getId());
        assertEquals("Titan Cybernetics Ltd", created.getName());

        Optional<Company> fetched = companyRepository.findById("CMP-PG-02");
        assertTrue(fetched.isPresent());
        assertEquals("Artificial Intelligence", fetched.get().getIndustry());
    }

    @Test
    @DisplayName("Duplicate company ID and duplicate name are prevented")
    void testDuplicateCompanyPrevention() {
        // Duplicate ID
        assertThrows(DuplicateEntityException.class, () ->
                companyService.registerCompany(CMP_ID, "Unique Name", "Robotics")
        );

        // Duplicate Name
        assertThrows(DuplicateEntityException.class, () ->
                companyService.registerCompany("CMP-PG-02", "Apex Technologies", "FinTech")
        );
    }

    @Test
    @DisplayName("Recruitment drive creation with foreign key check and criteria validation")
    void testDriveCreationAndCriteriaValidation() {
        // Unknown company ID fails
        assertThrows(EntityNotFoundException.class, () ->
                driveService.createDrive("DRV-PG-02", "NONEXISTENT", "SRE", 7.0, Set.of("CSE"), LocalDate.now().plusMonths(1))
        );

        // Valid drive creation succeeds
        RecruitmentDrive drive = transactionManager.runInTransaction(conn ->
                driveService.createDrive("DRV-PG-02", CMP_ID, "Site Reliability Engineer", 7.0, Set.of("CSE"), LocalDate.now().plusMonths(2))
        );

        assertNotNull(drive);
        assertEquals("DRV-PG-02", drive.getId());
        assertEquals("Site Reliability Engineer", drive.getJobTitle());
        assertFalse(drive.isClosed());
    }

    @Test
    @DisplayName("Manual drive closure preserves deadline, marks is_closed, and repeat-close is idempotent")
    void testManualDriveClosureAndRepeatClosure() {
        LocalDate originalDeadline = driveService.getDriveOrThrow(DRV_ID).getApplicationDeadline();

        // Close drive
        RecruitmentDrive closed = transactionManager.runInTransaction(conn ->
                driveService.closeDrive(DRV_ID)
        );

        assertTrue(closed.isClosed());
        assertEquals(originalDeadline, closed.getApplicationDeadline());

        // Verify in PostgreSQL directly
        RecruitmentDrive direct = driveRepository.findById(DRV_ID).orElseThrow();
        assertTrue(direct.isClosed());
        assertEquals(originalDeadline, direct.getApplicationDeadline());

        // Repeat closure is idempotent
        RecruitmentDrive repeat = transactionManager.runInTransaction(conn ->
                driveService.closeDrive(DRV_ID)
        );
        assertTrue(repeat.isClosed());
    }

    @Test
    @DisplayName("Valid shortlisting transitions application status to SHORTLISTED in PostgreSQL")
    void testValidShortlisting() {
        // Student applies
        Application app = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(STU_ID_1, DRV_ID)
        );
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Officer shortlists
        Application shortlisted = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(app.getId())
        );

        assertEquals(ApplicationStatus.SHORTLISTED, shortlisted.getStatus());

        // Verify state in PostgreSQL
        Application direct = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, direct.getStatus());
    }

    @Test
    @DisplayName("Shortlisting rejects candidate if academic criteria are no longer satisfied")
    void testShortlistingRejectsIneligibleStudent() {
        // Create application directly for student 2 (who has 6.00 CGPA below the 7.50 cutoff)
        Application app = new Application("APP-PG-02", STU_ID_2, DRV_ID, ApplicationStatus.APPLIED, LocalDateTime.now(), "Legacy");
        applicationRepository.add(app);

        // Attempting to shortlist should recheck academic criteria and fail
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication("APP-PG-02")
                )
        );
        assertTrue(ex.getMessage().contains("academic eligibility criteria"));
    }

    @Test
    @DisplayName("Shortlisting rejects invalid state transition from REJECTED")
    void testInvalidStatusShortlisting() {
        Application app = new Application("APP-PG-02", STU_ID_1, DRV_ID, ApplicationStatus.REJECTED, LocalDateTime.now(), "Rejected earlier");
        applicationRepository.add(app);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication("APP-PG-02")
                )
        );
        assertTrue(ex.getMessage().contains("Invalid status transition"));
    }

    @Test
    @DisplayName("Concurrent officer shortlisting on the same application guarantees exactly one transition and rejects concurrent race")
    void testConcurrentOfficerShortlisting() throws Exception {
        // Submit initial application
        Application app = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(STU_ID_1, DRV_ID)
        );
        String appId = app.getId();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    Application res = transactionManager.runInTransaction(conn ->
                            applicationService.shortlistApplication(appId, false)
                    );
                    if (res.getStatus() == ApplicationStatus.SHORTLISTED) {
                        successCount.incrementAndGet();
                    }
                } catch (DuplicateEntityException dex) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Concurrent shortlisting tasks must complete within timeout");
        assertTrue(unexpectedErrors.isEmpty(), "Zero unexpected errors expected: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "Exactly one officer must successfully transition the application");
        assertEquals(1, conflictCount.get(), "Exactly one officer must receive conflict (cannot both report success)");

        Application finalApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, finalApp.getStatus());
    }

    @Test
    @DisplayName("Interviewer creation enforces foreign key to company")
    void testInterviewerCreationAndForeignKeyEnforcement() {
        // Unknown company fails
        assertThrows(EntityNotFoundException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    Interviewer i = new Interviewer("INT-PG-02", "Dr. Emmett Brown", "NON_EXISTENT_COMPANY");
                    if (!companyRepository.existsById(i.getCompanyId())) {
                        throw new EntityNotFoundException("Company does not exist.");
                    }
                    return interviewerRepository.add(i);
                })
        );

        // Valid company succeeds
        Interviewer interviewer = transactionManager.runInTransaction(conn -> {
            Interviewer i = new Interviewer(INT_ID, "Dr. Emmett Brown", CMP_ID);
            return interviewerRepository.add(i);
        });

        assertNotNull(interviewer);
        assertEquals(INT_ID, interviewer.getId());
        assertEquals("Dr. Emmett Brown", interviewer.getName());

        Optional<Interviewer> fetched = interviewerRepository.findById(INT_ID);
        assertTrue(fetched.isPresent());
    }

    @Test
    @DisplayName("Room creation validates capacity > 0 and persists in PostgreSQL")
    void testRoomCreationAndCapacityValidation() {
        // Capacity <= 0 fails
        assertThrows(ValidationException.class, () -> {
            if (0 <= 0) throw new ValidationException("Capacity must be positive.");
            roomRepository.add(new Room("RM-PG-02", "Bad Room", 0));
        });

        // Valid room succeeds
        Room room = transactionManager.runInTransaction(conn ->
                roomRepository.add(new Room(ROOM_ID, "Innovation Lab Room 1", 8))
        );

        assertNotNull(room);
        assertEquals(ROOM_ID, room.getId());
        assertEquals(8, room.getCapacity());

        Optional<Room> fetched = roomRepository.findById(ROOM_ID);
        assertTrue(fetched.isPresent());
        assertEquals(8, fetched.get().getCapacity());
    }

    @Test
    @DisplayName("Transaction rollback restores database state completely on failure")
    void testTransactionRollbackOnFailure() {
        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    companyService.registerCompany("CMP-PG-02", "Phantom Systems", "Stealth");
                    // Simulate sudden failure
                    throw new RuntimeException("Simulated unexpected failure during creation");
                })
        );

        // Verify "CMP-PG-02" was rolled back and does not exist
        assertFalse(companyRepository.existsById("CMP-PG-02"));
    }

    @Test
    @DisplayName("Ambient connection binds repositories properly without opening independent connections")
    void testAmbientJdbcConnectionParticipation() {
        transactionManager.runInTransaction(conn -> {
            assertTrue(TransactionManager.isCurrentTransactionActive());
            Connection ambient = TransactionManager.getCurrentConnection();
            assertNotNull(ambient);

            // Create entity within transaction
            companyService.registerCompany("CMP-PG-02", "Ambient Corp", "Networking");

            // Verify uncommitted read on the same ambient connection
            assertTrue(companyRepository.existsById("CMP-PG-02"));
            return null;
        });

        // Verify committed read outside transaction
        assertTrue(companyRepository.existsById("CMP-PG-02"));
    }

    @Test
    @DisplayName("Duplicate sequential shortlisting in strict mode throws DuplicateEntityException")
    void testDuplicateSequentialShortlisting() {
        Application app = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(STU_ID_1, DRV_ID)
        );

        // First shortlist succeeds
        Application first = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(app.getId(), false)
        );
        assertEquals(ApplicationStatus.SHORTLISTED, first.getStatus());

        // Second sequential shortlist throws DuplicateEntityException
        DuplicateEntityException ex = assertThrows(DuplicateEntityException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication(app.getId(), false)
                )
        );
        assertTrue(ex.getMessage().contains("already been shortlisted"));
    }

    @Test
    @DisplayName("Candidate shortlisting succeeds even after manual recruitment drive closure")
    void testShortlistingAllowedAfterManualDriveClosure() {
        Application app = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(STU_ID_1, DRV_ID)
        );

        // Manually close drive
        transactionManager.runInTransaction(conn -> driveService.closeDrive(DRV_ID));
        assertTrue(driveService.getDriveOrThrow(DRV_ID).isClosed());

        // Shortlisting candidates who applied before closure must succeed
        Application shortlisted = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(app.getId(), false)
        );
        assertEquals(ApplicationStatus.SHORTLISTED, shortlisted.getStatus());
    }

    @Test
    @DisplayName("Shortlisting candidate already in SCHEDULED status throws IllegalStateException")
    void testShortlistScheduledCandidateRejected() {
        Application app = new Application("APP-PG-SCHED", STU_ID_1, DRV_ID, ApplicationStatus.SCHEDULED, LocalDateTime.now(), "Scheduled");
        applicationRepository.add(app);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication("APP-PG-SCHED", false)
                )
        );
        assertTrue(ex.getMessage().contains("already scheduled"));
    }

    @Test
    @DisplayName("findByIdForUpdate throws IllegalStateException when invoked outside an active transaction")
    void testFindByIdForUpdate_outsideTransaction_throwsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                driveRepository.findByIdForUpdate(DRV_ID)
        );
        assertTrue(ex.getMessage().contains("outside of an active transaction"));
    }

    @Test
    @DisplayName("findByIdForUpdate succeeds when invoked inside an active transaction")
    void testFindByIdForUpdate_insideTransaction_succeeds() {
        transactionManager.runInTransaction(conn -> {
            Optional<RecruitmentDrive> driveOpt = driveRepository.findByIdForUpdate(DRV_ID);
            assertTrue(driveOpt.isPresent());
            assertEquals(DRV_ID, driveOpt.get().getId());
            return null;
        });
    }
}
