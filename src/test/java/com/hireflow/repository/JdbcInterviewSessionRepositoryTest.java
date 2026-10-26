package com.hireflow.repository;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.SchedulingConflictException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.InterviewService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2B.5 Integration Tests for PostgreSQL JDBC Interview Session Persistence:
 * - {@link JdbcInterviewSessionRepository}
 * - Transactional schedule publication via {@link InterviewService}
 * - PostgreSQL GiST exclusion constraints: interviewer, room, and student overlap prevention
 * - 15-minute buffer enforcement across back-to-back sessions
 * - Global lock ordering (Rooms -> Interviewers -> Students) and concurrent publication safety
 * - Atomic rollback on post-insertion failure and idempotent publication
 *
 * Runs strictly against hireflow_test with deterministic ID prefixes ('TEST-').
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JdbcInterviewSessionRepositoryTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    private JdbcCompanyRepository companyRepo;
    private JdbcStudentRepository studentRepo;
    private JdbcDriveRepository driveRepo;
    private JdbcInterviewerRepository interviewerRepo;
    private JdbcRoomRepository roomRepo;
    private JdbcApplicationRepository applicationRepo;
    private JdbcInterviewSessionRepository sessionRepo;
    private InterviewService interviewService;
    private ApplicationService applicationService;

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
        interviewerRepo = new JdbcInterviewerRepository(dataSourceProvider.getDataSource());
        roomRepo = new JdbcRoomRepository(dataSourceProvider.getDataSource());
        applicationRepo = new JdbcApplicationRepository(dataSourceProvider.getDataSource());
        sessionRepo = new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource());

        applicationService = new ApplicationService(applicationRepo, studentRepo, driveRepo, new EligibilityService());
        interviewService = new InterviewService(sessionRepo, applicationRepo, applicationService, transactionManager);

        cleanTestData();

        // 1. Seed Companies
        companyRepo.save(new Company("TEST-CMP-SES-01", "Alpha Tech", "Software"));
        companyRepo.save(new Company("TEST-CMP-SES-02", "Beta Finance", "Banking"));

        // 2. Seed Recruitment Drives
        driveRepo.save(new RecruitmentDrive("TEST-DRV-SES-01", "TEST-CMP-SES-01", "SWE Drive",
                7.5, Set.of("CSE", "ECE"), LocalDate.now().plusDays(10)));
        driveRepo.save(new RecruitmentDrive("TEST-DRV-SES-02", "TEST-CMP-SES-02", "Quant Drive",
                8.0, Set.of("CSE"), LocalDate.now().plusDays(15)));

        // 3. Seed Students
        studentRepo.save(new Student("TEST-STU-SES-01", "Alice Student", "alice.ses@test.com", "CSE", 8.5, 2026));
        studentRepo.save(new Student("TEST-STU-SES-02", "Bob Student", "bob.ses@test.com", "ECE", 7.8, 2026));

        // 4. Seed Interviewers
        interviewerRepo.save(new Interviewer("TEST-INT-SES-01", "Dr. Alan", "TEST-CMP-SES-01"));
        interviewerRepo.save(new Interviewer("TEST-INT-SES-02", "Dr. Grace", "TEST-CMP-SES-02"));

        // 5. Seed Rooms
        roomRepo.save(new Room("TEST-RM-SES-01", "Interview Room 101", 5));
        roomRepo.save(new Room("TEST-RM-SES-02", "Interview Room 102", 5));

        // 6. Seed Applications with appropriate initial status
        applicationRepo.save(new Application("TEST-APP-SES-01", "TEST-STU-SES-01", "TEST-DRV-SES-01", ApplicationStatus.SHORTLISTED));
        applicationRepo.save(new Application("TEST-APP-SES-02", "TEST-STU-SES-02", "TEST-DRV-SES-01", ApplicationStatus.SHORTLISTED));
        applicationRepo.save(new Application("TEST-APP-SES-03", "TEST-STU-SES-01", "TEST-DRV-SES-02", ApplicationStatus.SHORTLISTED));
        applicationRepo.save(new Application("TEST-APP-SES-04", "TEST-STU-SES-02", "TEST-DRV-SES-02", ApplicationStatus.APPLIED)); // Not shortlisted
    }

    private static void cleanTestData() {
        com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
    }

    // =========================================================================
    // 1. Insert and retrieve a valid interview session
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("1: Insert and retrieve a valid interview session")
    void testInsertAndRetrieveValidSession() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);

        InterviewSession session = new InterviewSession(
                "TEST-SES-001", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end
        );

        InterviewSession saved = sessionRepo.add(session);
        assertNotNull(saved);
        assertEquals("TEST-SES-001", saved.getId());
        assertEquals("TEST-APP-SES-01", saved.getApplicationId());
        assertEquals("TEST-INT-SES-01", saved.getInterviewerId());
        assertEquals("TEST-RM-SES-01", saved.getRoomId());
        assertEquals(start, saved.getStartTime());
        assertEquals(end, saved.getEndTime());

        Optional<InterviewSession> foundOpt = sessionRepo.findById("TEST-SES-001");
        assertTrue(foundOpt.isPresent());
        InterviewSession found = foundOpt.get();
        assertEquals("TEST-SES-001", found.getId());
        assertEquals(start, found.getStartTime());
        assertEquals(end, found.getEndTime());
        assertTrue(sessionRepo.existsById("TEST-SES-001"));
        assertEquals(1, sessionRepo.count());
    }

    // =========================================================================
    // 2. Update and delete permitted unreferenced sessions
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("2: Update and delete permitted unreferenced interview session")
    void testUpdateAndDeleteSession() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession(
                "TEST-SES-002", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end
        );
        sessionRepo.add(session);

        // Update room and time
        LocalDateTime newStart = LocalDateTime.of(2026, 10, 20, 14, 0);
        LocalDateTime newEnd = LocalDateTime.of(2026, 10, 20, 15, 0);
        session.setRoomId("TEST-RM-SES-02");
        session.setTimeInterval(newStart, newEnd);

        InterviewSession updated = sessionRepo.update(session);
        assertEquals("TEST-RM-SES-02", updated.getRoomId());
        assertEquals(newStart, updated.getStartTime());

        InterviewSession retrieved = sessionRepo.findById("TEST-SES-002").orElseThrow();
        assertEquals("TEST-RM-SES-02", retrieved.getRoomId());
        assertEquals(newStart, retrieved.getStartTime());

        // Delete session
        boolean deleted = sessionRepo.deleteById("TEST-SES-002");
        assertTrue(deleted);
        assertFalse(sessionRepo.existsById("TEST-SES-002"));
    }

    // =========================================================================
    // 3. Reject duplicate session IDs
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("3: Reject duplicate session IDs")
    void testRejectDuplicateSessionId() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession s1 = new InterviewSession("TEST-SES-DUP", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);
        sessionRepo.add(s1);

        InterviewSession s2 = new InterviewSession("TEST-SES-DUP", "TEST-APP-SES-02", "TEST-INT-SES-02", "TEST-RM-SES-02",
                start.plusHours(2), end.plusHours(2));
        assertThrows(DuplicateEntityException.class, () -> sessionRepo.add(s2));
    }

    // =========================================================================
    // 4. Reject multiple sessions for the same application
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("4: Reject multiple sessions for the same application (uq_session_application)")
    void testRejectMultipleSessionsForSameApplication() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession s1 = new InterviewSession("TEST-SES-APP-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);
        sessionRepo.add(s1);

        InterviewSession s2 = new InterviewSession("TEST-SES-APP-2", "TEST-APP-SES-01", "TEST-INT-SES-02", "TEST-RM-SES-02",
                start.plusHours(2), end.plusHours(2));
        assertThrows(DuplicateEntityException.class, () -> sessionRepo.add(s2));
    }

    // =========================================================================
    // 5. Reject nonexistent application references
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("5: Reject nonexistent application references")
    void testRejectNonexistentApplicationReference() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession badAppSession = new InterviewSession(
                "TEST-SES-BAD-APP", "TEST-APP-NONEXISTENT", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end
        );
        assertThrows(ValidationException.class, () -> sessionRepo.add(badAppSession));
    }

    // =========================================================================
    // 6. Reject mismatched student/application identities
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("6: Reject mismatched student/application identities (fk_sessions_application_student)")
    void testRejectMismatchedStudentApplicationIdentities() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession(
                "TEST-SES-MISMATCH", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end
        );

        // TEST-APP-SES-01 belongs to TEST-STU-SES-01; passing candidate student TEST-STU-SES-02 must fail
        assertThrows(ValidationException.class, () ->
                sessionRepo.addWithStudentId(session, "TEST-STU-SES-02"));

        // Direct SQL insertion with mismatched student ID must fail composite FK
        assertThrows(ValidationException.class, () -> {
            try (Connection conn = dataSourceProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, "TEST-SES-MISMATCH-SQL");
                ps.setString(2, "TEST-APP-SES-01");
                ps.setString(3, "TEST-STU-SES-02"); // Mismatched student ID
                ps.setString(4, "TEST-INT-SES-01");
                ps.setString(5, "TEST-RM-SES-01");
                ps.setTimestamp(6, Timestamp.valueOf(start));
                ps.setTimestamp(7, Timestamp.valueOf(end));
                ps.executeUpdate();
            } catch (java.sql.SQLException ex) {
                throw com.hireflow.db.SQLExceptionTranslator.translate("Direct composite FK insert", ex);
            }
        });
    }

    // =========================================================================
    // 7. Reject interviewer overlaps (GiST constraint 23P01)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("7: Reject interviewer overlaps (PostgreSQL GiST 23P01 -> SchedulingConflictException)")
    void testRejectInterviewerOverlaps() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        sessionRepo.add(new InterviewSession("TEST-SES-INT-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1));

        // Overlapping slot for same interviewer (10:30 - 11:30) with different room and student
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 11, 30);
        InterviewSession s2 = new InterviewSession("TEST-SES-INT-2", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-02", start2, end2);

        assertThrows(SchedulingConflictException.class, () -> sessionRepo.add(s2));
    }

    // =========================================================================
    // 8. Reject room overlaps (GiST constraint 23P01)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("8: Reject room overlaps (PostgreSQL GiST 23P01 -> SchedulingConflictException)")
    void testRejectRoomOverlaps() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        sessionRepo.add(new InterviewSession("TEST-SES-RM-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1));

        // Overlapping slot for same room (10:30 - 11:30) with different interviewer and student
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 11, 30);
        InterviewSession s2 = new InterviewSession("TEST-SES-RM-2", "TEST-APP-SES-02", "TEST-INT-SES-02", "TEST-RM-SES-01", start2, end2);

        assertThrows(SchedulingConflictException.class, () -> sessionRepo.add(s2));
    }

    // =========================================================================
    // 9. Reject same student being interviewed simultaneously by two companies
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("9: Reject same student interviewed simultaneously by two companies (GiST 23P01)")
    void testRejectStudentSimultaneousInterviews() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        // Student 1 interviewed by Alpha Tech (TEST-APP-SES-01)
        sessionRepo.add(new InterviewSession("TEST-SES-STU-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1));

        // Same Student 1 proposed for Beta Finance (TEST-APP-SES-03) during overlapping slot (10:30 - 11:30)
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 11, 30);
        InterviewSession s2 = new InterviewSession("TEST-SES-STU-2", "TEST-APP-SES-03", "TEST-INT-SES-02", "TEST-RM-SES-02", start2, end2);

        assertThrows(SchedulingConflictException.class, () -> sessionRepo.add(s2));
    }

    // =========================================================================
    // 10 & 11: 15-minute buffer requirement & valid back-to-back sessions
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("10: Accept valid back-to-back sessions when 15-minute buffer permits")
    void testAcceptValidBackToBackSessionsWithBuffer() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession s1 = new InterviewSession("TEST-SES-BUF-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);

        // Exact 15-minute buffer: 11:15 - 12:15
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 11, 15);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 12, 15);
        InterviewSession s2 = new InterviewSession("TEST-SES-BUF-2", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-01", start2, end2);

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s1, s2), Duration.ofMinutes(15));
        assertEquals(2, result.publishedCount());
        assertTrue(result.conflictRejections().isEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("11: Reject sessions violating 15-minute buffer requirement (e.g. 10-minute gap)")
    void testRejectSessionsViolatingBufferRequirement() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession s1 = new InterviewSession("TEST-SES-BUF-3", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);

        // Only 10-minute gap: 11:10 - 12:10 (violates 15-minute buffer)
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 11, 10);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 12, 10);
        InterviewSession s2 = new InterviewSession("TEST-SES-BUF-4", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-01", start2, end2);

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s1, s2), Duration.ofMinutes(15));
        assertEquals(1, result.publishedCount());
        assertEquals(1, result.conflictRejections().size());
        assertTrue(result.conflictRejections().get(0).contains("conflict"));
    }

    // =========================================================================
    // 12. Respect existing confirmed appointments
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("12: Respect existing confirmed appointments during subsequent publication")
    void testRespectExistingConfirmedAppointments() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession s1 = new InterviewSession("TEST-SES-CONF-1", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);
        interviewService.publishSchedule(List.of(s1), Duration.ofMinutes(15));

        // Attempting to publish another session that collides with confirmed session s1
        InterviewSession s2 = new InterviewSession("TEST-SES-CONF-2", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);
        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s2), Duration.ofMinutes(15));

        assertEquals(0, result.publishedCount());
        assertEquals(1, result.conflictRejections().size());
    }

    // =========================================================================
    // 13 & 14: Successfully publish shortlisted application & verify SCHEDULED status
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("13 & 14: Successfully publish shortlisted application and verify SCHEDULED status transition")
    void testSuccessfulPublicationAndStatusTransition() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 10, 0);
        InterviewSession session = new InterviewSession("TEST-SES-PUB-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);

        // Before publication: Application is SHORTLISTED
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepo.findById("TEST-APP-SES-01").orElseThrow().getStatus());

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(1, result.publishedCount());

        // After publication: Application is SCHEDULED
        Application app = applicationRepo.findById("TEST-APP-SES-01").orElseThrow();
        assertEquals(ApplicationStatus.SCHEDULED, app.getStatus());
        assertTrue(app.getRemarks().contains("Interview confirmed"));
        assertTrue(sessionRepo.existsById("TEST-SES-PUB-01"));
    }

    // =========================================================================
    // 15. Simulate failure after session insertion and verify full rollback
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("15: Simulate failure after session insertion and verify full transaction rollback")
    void testRollbackOnPostInsertionFailure() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession("TEST-SES-FAIL-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);

        // Simulate transactional failure: insert session then throw an exception before commit
        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    sessionRepo.add(session);
                    // Deliberate simulated failure
                    throw new RuntimeException("Simulated catastrophic failure before transaction commit");
                })
        );

        // Verify entire transaction rolled back: session does not exist, application remains SHORTLISTED
        assertFalse(sessionRepo.existsById("TEST-SES-FAIL-01"));
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepo.findById("TEST-APP-SES-01").orElseThrow().getStatus());
    }

    // =========================================================================
    // 16. Prevent repeated schedule publication
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("16: Prevent repeated schedule publication (idempotent duplicate detection)")
    void testPreventRepeatedSchedulePublication() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession("TEST-SES-REP-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);

        InterviewService.PublicationResult firstResult = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(1, firstResult.publishedCount());

        // Repeated publication attempt
        InterviewService.PublicationResult secondResult = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(0, secondResult.publishedCount());
        assertEquals(1, secondResult.skippedDuplicateApplicationIds().size());
        assertEquals("TEST-APP-SES-01", secondResult.skippedDuplicateApplicationIds().get(0));
    }

    // =========================================================================
    // 17. Reject publication of non-shortlisted application
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("17: Reject publication of non-shortlisted application")
    void testRejectPublicationOfNonShortlistedApplication() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        // TEST-APP-SES-04 is in APPLIED status, not SHORTLISTED
        InterviewSession session = new InterviewSession("TEST-SES-NONSHORT", "TEST-APP-SES-04", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(0, result.publishedCount());
        assertEquals(1, result.conflictRejections().size());
        assertTrue(result.conflictRejections().get(0).contains("expected SHORTLISTED"));
    }

    // =========================================================================
    // 18. Publish partial schedule containing valid and conflicting candidates
    // =========================================================================

    @Test
    @Order(18)
    @DisplayName("18: Publish partial schedule containing valid and conflicting candidates")
    void testPublishPartialSchedule() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        // Valid Candidate 1
        InterviewSession s1 = new InterviewSession("TEST-SES-PART-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);

        // Conflicting Candidate 2 (overlaps with Candidate 1's interviewer in a different room)
        InterviewSession s2 = new InterviewSession("TEST-SES-PART-02", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-02", start1, end1);

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s1, s2), Duration.ofMinutes(15));

        // Candidate 1 succeeded, Candidate 2 rejected
        assertEquals(1, result.publishedCount());
        assertEquals(1, result.conflictRejections().size());
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepo.findById("TEST-APP-SES-01").orElseThrow().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepo.findById("TEST-APP-SES-02").orElseThrow().getStatus());
    }

    // =========================================================================
    // 19. Concurrent overlapping publication using separate JDBC connections
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("19: Concurrent overlapping publication safety across threads (Room/Interviewer locks)")
    void testConcurrentOverlappingPublication() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 15, 0);

        InterviewSession s1 = new InterviewSession("TEST-SES-CON-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);
        InterviewSession s2 = new InterviewSession("TEST-SES-CON-02", "TEST-APP-SES-02", "TEST-INT-SES-01", "TEST-RM-SES-02", start, end);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger publishedCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService svc = new InterviewService(
                        new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource()),
                        new JdbcApplicationRepository(dataSourceProvider.getDataSource()),
                        applicationService, transactionManager);
                InterviewService.PublicationResult res = svc.publishSchedule(List.of(s1), Duration.ofMinutes(15));
                publishedCount.addAndGet(res.publishedCount());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService svc = new InterviewService(
                        new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource()),
                        new JdbcApplicationRepository(dataSourceProvider.getDataSource()),
                        applicationService, transactionManager);
                InterviewService.PublicationResult res = svc.publishSchedule(List.of(s2), Duration.ofMinutes(15));
                publishedCount.addAndGet(res.publishedCount());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(1, publishedCount.get(), "Exactly one concurrent overlapping session must be published");
    }

    // =========================================================================
    // 20. Concurrent publication against the same application
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("20: Concurrent publication against the same application (Application row locking)")
    void testConcurrentPublicationAgainstSameApplication() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2026, 10, 20, 14, 0);
        LocalDateTime end2 = LocalDateTime.of(2026, 10, 20, 15, 0);

        InterviewSession s1 = new InterviewSession("TEST-SES-SAME-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);
        InterviewSession s2 = new InterviewSession("TEST-SES-SAME-02", "TEST-APP-SES-01", "TEST-INT-SES-02", "TEST-RM-SES-02", start2, end2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger publishedCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService svc = new InterviewService(
                        new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource()),
                        new JdbcApplicationRepository(dataSourceProvider.getDataSource()),
                        applicationService, transactionManager);
                InterviewService.PublicationResult res = svc.publishSchedule(List.of(s1), Duration.ofMinutes(15));
                publishedCount.addAndGet(res.publishedCount());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService svc = new InterviewService(
                        new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource()),
                        new JdbcApplicationRepository(dataSourceProvider.getDataSource()),
                        applicationService, transactionManager);
                InterviewService.PublicationResult res = svc.publishSchedule(List.of(s2), Duration.ofMinutes(15));
                publishedCount.addAndGet(res.publishedCount());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(1, publishedCount.get(), "An application can only be confirmed for exactly one interview appointment");
        assertEquals(1, sessionRepo.findByApplicationId("TEST-APP-SES-01").size());
    }

    // =========================================================================
    // 21. Test persistence after reconnecting
    // =========================================================================

    @Test
    @Order(21)
    @DisplayName("21: Test persistence across separate repository instances (reconnecting)")
    void testPersistenceAfterReconnecting() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession("TEST-SES-RECONN", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);
        sessionRepo.add(session);

        // Separate fresh repository instance simulating reconnection
        JdbcInterviewSessionRepository separateRepo = new JdbcInterviewSessionRepository(dataSourceProvider.getDataSource());
        Optional<InterviewSession> opt = separateRepo.findById("TEST-SES-RECONN");
        assertTrue(opt.isPresent());
        assertEquals("TEST-SES-RECONN", opt.get().getId());
        assertEquals(start, opt.get().getStartTime());
        assertEquals(end, opt.get().getEndTime());
    }

    // =========================================================================
    // 22. Test idempotent publication results
    // =========================================================================

    @Test
    @Order(22)
    @DisplayName("22: Test idempotent publication results")
    void testIdempotentPublicationResults() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession("TEST-SES-IDEM", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end);

        InterviewService.PublicationResult r1 = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(1, r1.publishedCount());

        InterviewService.PublicationResult r2 = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(0, r2.publishedCount());
        assertEquals(1, r2.skippedDuplicateApplicationIds().size());

        InterviewService.PublicationResult r3 = interviewService.publishSchedule(List.of(session), Duration.ofMinutes(15));
        assertEquals(0, r3.publishedCount());
        assertEquals(1, r3.skippedDuplicateApplicationIds().size());
    }

    // =========================================================================
    // 23. Test repeatability and isolation of integration tests
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("23: Test repeatability and isolation of integration tests")
    void testRepeatabilityAndIsolation() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 20, 11, 0);
        sessionRepo.add(new InterviewSession("TEST-SES-ISO-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start, end));
        assertEquals(1, sessionRepo.count());

        cleanTestData();
        assertEquals(0, sessionRepo.count());
    }

    // =========================================================================
    // 24. Save upsert semantics
    // =========================================================================

    @Test
    @Order(24)
    @DisplayName("24: save() adds if not present and updates if already present")
    void testSaveUpsertSemantics() {
        LocalDateTime start1 = LocalDateTime.of(2026, 10, 20, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2026, 10, 20, 11, 0);
        InterviewSession session = new InterviewSession("TEST-SES-SAVE-01", "TEST-APP-SES-01", "TEST-INT-SES-01", "TEST-RM-SES-01", start1, end1);

        sessionRepo.save(session);
        assertEquals("TEST-RM-SES-01", sessionRepo.findById("TEST-SES-SAVE-01").orElseThrow().getRoomId());

        session.setRoomId("TEST-RM-SES-02");
        sessionRepo.save(session);
        assertEquals("TEST-RM-SES-02", sessionRepo.findById("TEST-SES-SAVE-01").orElseThrow().getRoomId());
    }
}
