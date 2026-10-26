package com.hireflow.api.officer;

import com.hireflow.api.service.SchedulingPreviewCacheService;
import com.hireflow.db.TransactionManager;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.ConflictChecker;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.scheduler.SchedulingRequest;
import com.hireflow.scheduler.TimeWindow;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.InterviewService;
import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;
import com.hireflow.web.dto.SchedulingPreviewState;
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
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("SchedulingConcurrencyAndIntegrityPostgresTest - PostgreSQL Lock Ordering, GiST Constraints, Anti-Replay & Rollback Safety")
class SchedulingConcurrencyAndIntegrityPostgresTest {
    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource cleanupDataSource;


    @Autowired
    private InterviewService interviewService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SchedulingPreviewCacheService previewCacheService;

    @Autowired
    @Qualifier("hireflowJdbcTransactionManager")
    private TransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private static final String CMP_ID = "CMP-PG-SCH";
    private static final String DRV_ID = "DRV-PG-SCH";
    private static final String STU_1_ID = "STU-PG-SCH1";
    private static final String STU_2_ID = "STU-PG-SCH2";
    private static final String APP_1_ID = "APP-PG-SCH1";
    private static final String APP_2_ID = "APP-PG-SCH2";
    private static final String INT_1_ID = "INT-PG-SCH1";
    private static final String ROOM_1_ID = "RM-PG-SCH1";

    private final List<HireFlowEvent> recordedEvents = new CopyOnWriteArrayList<>();
    private final HireFlowEventListener testListener = recordedEvents::add;

    @BeforeEach
    void setUp() {
        cleanTestData();
        recordedEvents.clear();
        interviewService.addEventListener(testListener);

        // 1. Company
        companyRepository.add(new Company(CMP_ID, "Apex Cloud Systems", "Cloud"));

        // 2. Drive
        driveRepository.add(new RecruitmentDrive(
                DRV_ID, CMP_ID, "Distributed Systems Engineer", 7.50, Set.of("CSE", "IT"),
                LocalDate.now().plusMonths(2), false
        ));

        // 3. Students
        studentRepository.add(new Student(STU_1_ID, "Ada Lovelace", "ada@hireflow.test", "CSE", 9.80, 2026, true));
        studentRepository.add(new Student(STU_2_ID, "Alan Turing", "alan@hireflow.test", "CSE", 9.50, 2026, true));

        // 4. Shortlisted Applications
        applicationRepository.add(new Application(APP_1_ID, STU_1_ID, DRV_ID, ApplicationStatus.SHORTLISTED, LocalDateTime.now().minusDays(2), "Shortlisted"));
        applicationRepository.add(new Application(APP_2_ID, STU_2_ID, DRV_ID, ApplicationStatus.SHORTLISTED, LocalDateTime.now().minusDays(1), "Shortlisted"));

        // 5. Interviewer & Room
        interviewerRepository.add(new Interviewer(INT_1_ID, "Grace Hopper", CMP_ID));
        roomRepository.add(new Room(ROOM_1_ID, "Turing Suite", 10));
    }

    @AfterEach
    void tearDown() {
        interviewService.removeEventListener(testListener);
        cleanTestData();
        previewCacheService.clear();
    }

    private void cleanTestData() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"CMP-PG-SCH", "DRV-PG-SCH", "STU-PG-SCH1", "STU-PG-SCH2", "APP-PG-SCH1", "APP-PG-SCH2", "INT-PG-SCH1", "RM-PG-SCH1", "DRV-PG-SCH2", "INT-PG-ALT", "INT-PG-SCH2", "RM-PG-SCH2", "CMP-PG-SCH2", "STU-PG-SCH3", "SES-CONC-1", "SES-CONC-RM1", "SES-CONC-RM2", "SES-LOCK-", "SES-STALE-01", "SES-EVT-01", "SES-GIST-INT1", "SES-GIST-INT2", "SES-GIST-RM1", "SES-GIST-RM2", "APP-PG-DRV2", "SES-GIST-STU1", "SES-GIST-STU2", "SES-BND-1", "SES-BND-2", "SES-CHK-INV", "SES-PARTIAL-1", "SES-PARTIAL-2", "SES-PERSIST-1"},
            new String[]{});
    }

    @Test
    @DisplayName("Advisory Preview Immutability: GreedyScheduler execution mutates zero database rows")
    void testAdvisoryPreview_zeroDatabaseMutations() {
        GreedyScheduler scheduler = new GreedyScheduler(new ConflictChecker());
        TimeWindow window = new TimeWindow(
                LocalDateTime.now().plusDays(1).withHour(9).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(17).withMinute(0)
        );

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(applicationRepository.findByDriveId(DRV_ID))
                .interviewers(List.of(interviewerRepository.findById(INT_1_ID).get()))
                .rooms(List.of(roomRepository.findById(ROOM_1_ID).get()))
                .schedulingWindow(window)
                .interviewDuration(Duration.ofMinutes(45))
                .bufferDuration(Duration.ofMinutes(15))
                .existingSessions(sessionRepository.findAll())
                .addDriveCompany(DRV_ID, CMP_ID)
                .build();

        var result = scheduler.schedule(request);

        assertEquals(2, result.getTotalScheduled());
        assertEquals(0, result.getTotalUnscheduled());

        // Zero DB changes
        assertEquals(0, fixtureSessionCount(), "Zero sessions inserted during preview generation.");
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_1_ID).get().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_2_ID).get().getStatus());
        assertEquals(0, recordedEvents.size(), "Zero events broadcast during preview.");
    }

    @Test
    @DisplayName("Single-Use Anti-Replay: Concurrent publication with same preview token permits exactly one execution")
    void testConcurrentPreviewConsumption_antiReplay() throws Exception {
        String previewId = "PREV-CONCURRENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SchedulingPreviewState state = new SchedulingPreviewState(
                previewId,
                "officer_adam",
                java.time.Instant.now(),
                java.time.Instant.now().plus(SchedulingPreviewState.DEFAULT_MAX_AGE),
                false,
                DRV_ID,
                "Job",
                "Company",
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                45,
                15,
                1,
                1,
                0,
                List.of(new SchedulingPreviewState.ProposedSessionDto(
                        "SES-CONC-1", APP_1_ID, STU_1_ID, "Ada", "CSE", INT_1_ID, "Grace", ROOM_1_ID, "Turing Suite",
                        LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                        LocalDateTime.now().plusDays(1).withHour(10).withMinute(45)
                )),
                List.of()
        );
        previewCacheService.put(state);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    previewCacheService.consume(previewId, "officer_adam");
                    successCount.incrementAndGet();
                } catch (com.hireflow.api.exception.PreviewConsumedException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one consumer must successfully consume the preview token.");
        assertEquals(threadCount - 1, conflictCount.get(), "All other concurrent attempts must be rejected with PreviewConsumedException.");
    }

    @Test
    @DisplayName("PostgreSQL Concurrency: Competing publications contending for same room slot are safely reconciled")
    void testConcurrentRoomContention_safePublication() throws Exception {
        // Prepare two distinct applications contending for the exact same room and start time
        LocalDateTime slotStart = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0);
        LocalDateTime slotEnd = slotStart.plusMinutes(45);

        // App 1 session
        InterviewSession session1 = new InterviewSession("SES-CONC-RM1", APP_1_ID, INT_1_ID, ROOM_1_ID, slotStart, slotEnd);
        // App 2 session (same room, same time, but different interviewer to isolate room contention)
        String altIntId = "INT-PG-ALT";
        interviewerRepository.add(new Interviewer(altIntId, "Alan Kay", CMP_ID));
        InterviewSession session2 = new InterviewSession("SES-CONC-RM2", APP_2_ID, altIntId, ROOM_1_ID, slotStart, slotEnd);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<InterviewService.PublicationResult> results = new CopyOnWriteArrayList<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService.PublicationResult res = interviewService.publishSchedule(List.of(session1), Duration.ofMinutes(15));
                results.add(res);
            } catch (Exception ex) {
                // caught
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                InterviewService.PublicationResult res = interviewService.publishSchedule(List.of(session2), Duration.ofMinutes(15));
                results.add(res);
            } catch (Exception ex) {
                // caught
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly one should be published, and the other should record a room conflict!
        int totalPublished = results.stream().mapToInt(InterviewService.PublicationResult::publishedCount).sum();
        int totalConflicts = results.stream().mapToInt(r -> r.conflictRejections().size()).sum();

        assertEquals(1, totalPublished, "Exactly one appointment must be published in the contested room.");
        assertEquals(1, totalConflicts, "The overlapping appointment must be safely rejected due to room contention.");
        assertEquals(1, fixtureSessionCount(), "Database must persist exactly one session.");
    }

    @Test
    @DisplayName("Lock Order & Exclusion Constraints: Applications -> Rooms -> Interviewers -> Students prevents deadlocks")
    void testLockOrderingAndExclusionSafety() throws Exception {
        // Verify multiple concurrent transactions follow consistent lock ordering without deadlocks
        int workers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workers);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < workers; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each worker creates and confirms an independent slot
                    LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(9 + index).withMinute(0);
                    LocalDateTime end = start.plusMinutes(45);
                    String appId = (index % 2 == 0) ? APP_1_ID : APP_2_ID;

                    // Publish
                    interviewService.publishSchedule(List.of(
                            new InterviewSession("SES-LOCK-" + index, appId, INT_1_ID, ROOM_1_ID, start, end)
                    ), Duration.ofMinutes(15));
                } catch (Exception ex) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent publications completed without deadlock.");
        executor.shutdown();

        assertEquals(0, failures.get(), "Zero deadlock failures across concurrent publications.");
    }

    @Test
    @DisplayName("Stale Application Status: Status change away from SHORTLISTED aborts publication with conflict")
    void testStaleApplicationStatus_abortsPublication() {
        // Change application status from SHORTLISTED to REJECTED before publication
        applicationRepository.updateStatusConditional(APP_1_ID, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, "Not shortlisted");

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        InterviewSession proposed = new InterviewSession("SES-STALE-01", APP_1_ID, INT_1_ID, ROOM_1_ID, start, start.plusMinutes(45));

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(proposed), Duration.ofMinutes(15));

        assertEquals(0, result.publishedCount(), "No session should be published for non-SHORTLISTED application.");
        assertEquals(1, result.conflictRejections().size(), "Stale application status must produce conflict rejection.");
        assertTrue(result.conflictRejections().get(0).contains("expected SHORTLISTED"));
        assertEquals(0, fixtureSessionCount());
    }

    @Test
    @DisplayName("Post-Commit Event Lifecycle: Events are emitted only after successful database commit")
    void testPostCommitEventDispatch_noEventOnFailure() {
        // Valid publication
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        InterviewSession validSession = new InterviewSession("SES-EVT-01", APP_1_ID, INT_1_ID, ROOM_1_ID, start, start.plusMinutes(45));

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(validSession), Duration.ofMinutes(15));
        assertEquals(1, result.publishedCount());

        // Event listener should receive exactly 1 INTERVIEW_SCHEDULED event
        assertEquals(1, recordedEvents.size());
        assertEquals(HireFlowEventType.INTERVIEW_SCHEDULED, recordedEvents.get(0).type());
        assertEquals("SES-EVT-01", recordedEvents.get(0).sourceId());

        // Now attempt an invalid duplicate session publication
        recordedEvents.clear();
        InterviewService.PublicationResult dupResult = interviewService.publishSchedule(List.of(validSession), Duration.ofMinutes(15));
        assertEquals(0, dupResult.publishedCount());
        assertEquals(1, dupResult.skippedDuplicateApplicationIds().size());

        // NO new events emitted on skipped duplicate
        assertEquals(0, recordedEvents.size(), "Zero events broadcast for skipped duplicate publication.");
    }

    @Test
    @DisplayName("PostgreSQL GiST: Overlapping interviewer sessions violate no_overlapping_interviewer_sessions (23P01)")
    void testPostgresGistConstraint_interviewerOverlap() {
        LocalDateTime start1 = LocalDateTime.now().plusDays(4).withHour(10).withMinute(0);
        LocalDateTime end1 = start1.plusMinutes(45);
        InterviewSession session1 = new InterviewSession("SES-GIST-INT1", APP_1_ID, INT_1_ID, ROOM_1_ID, start1, end1);
        sessionRepository.add(session1);

        // Direct SQL insert attempting to book the same interviewer in a different room at an overlapping time
        roomRepository.add(new Room("RM-PG-SCH2", "Lovelace Lab", 8));
        LocalDateTime overlapStart = start1.plusMinutes(15);
        LocalDateTime overlapEnd = overlapStart.plusMinutes(45);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, "SES-GIST-INT2");
            ps.setString(2, APP_2_ID);
            ps.setString(3, STU_2_ID);
            ps.setString(4, INT_1_ID);
            ps.setString(5, "RM-PG-SCH2");
            ps.setObject(6, overlapStart);
            ps.setObject(7, overlapEnd);

            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "PostgreSQL GiST constraint no_overlapping_interviewer_sessions must reject overlapping interviewer sessions.");
            assertEquals("23P01", ex.getSQLState(), "Expected SQLSTATE 23P01 (exclusion_violation)");
            assertTrue(ex.getMessage().contains("no_overlapping_interviewer_sessions"),
                    "Error must reference constraint no_overlapping_interviewer_sessions");
        } catch (SQLException e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("PostgreSQL GiST: Overlapping room sessions violate no_overlapping_room_sessions (23P01)")
    void testPostgresGistConstraint_roomOverlap() {
        LocalDateTime start1 = LocalDateTime.now().plusDays(4).withHour(11).withMinute(0);
        LocalDateTime end1 = start1.plusMinutes(45);
        InterviewSession session1 = new InterviewSession("SES-GIST-RM1", APP_1_ID, INT_1_ID, ROOM_1_ID, start1, end1);
        sessionRepository.add(session1);

        // Direct SQL insert attempting to book the same room with different interviewer and student at overlapping time
        interviewerRepository.add(new Interviewer("INT-PG-SCH2", "Claude Shannon", CMP_ID));
        LocalDateTime overlapStart = start1.plusMinutes(15);
        LocalDateTime overlapEnd = overlapStart.plusMinutes(45);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, "SES-GIST-RM2");
            ps.setString(2, APP_2_ID);
            ps.setString(3, STU_2_ID);
            ps.setString(4, "INT-PG-SCH2");
            ps.setString(5, ROOM_1_ID);
            ps.setObject(6, overlapStart);
            ps.setObject(7, overlapEnd);

            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "PostgreSQL GiST constraint no_overlapping_room_sessions must reject overlapping room sessions.");
            assertEquals("23P01", ex.getSQLState(), "Expected SQLSTATE 23P01 (exclusion_violation)");
            assertTrue(ex.getMessage().contains("no_overlapping_room_sessions"),
                    "Error must reference constraint no_overlapping_room_sessions");
        } catch (SQLException e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("PostgreSQL GiST: Overlapping student sessions across different recruitment drives violate no_overlapping_student_sessions (23P01)")
    void testPostgresGistConstraint_studentOverlapAcrossDrives() {
        // Setup Drive 2 and an application for Student 1 in Drive 2
        String drv2Id = "DRV-PG-SCH2";
        String cmp2Id = "CMP-PG-SCH2";
        String appDrive2Id = "APP-PG-DRV2";
        companyRepository.add(new Company(cmp2Id, "HyperScale AI", "AI"));
        driveRepository.add(new RecruitmentDrive(drv2Id, cmp2Id, "AI Research Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusMonths(2), false));
        applicationRepository.add(new Application(appDrive2Id, STU_1_ID, drv2Id, ApplicationStatus.SHORTLISTED, LocalDateTime.now().minusDays(1), "Shortlisted in drive 2"));

        LocalDateTime start1 = LocalDateTime.now().plusDays(4).withHour(14).withMinute(0);
        LocalDateTime end1 = start1.plusMinutes(45);
        InterviewSession sessionDrive1 = new InterviewSession("SES-GIST-STU1", APP_1_ID, INT_1_ID, ROOM_1_ID, start1, end1);
        sessionRepository.add(sessionDrive1);

        // Direct SQL insert attempting to schedule Student 1 in Drive 2 with a different interviewer and room at overlapping time
        interviewerRepository.add(new Interviewer("INT-PG-SCH2", "Claude Shannon", cmp2Id));
        roomRepository.add(new Room("RM-PG-SCH2", "Lovelace Lab", 8));
        LocalDateTime overlapStart = start1.plusMinutes(20);
        LocalDateTime overlapEnd = overlapStart.plusMinutes(45);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, "SES-GIST-STU2");
            ps.setString(2, appDrive2Id);
            ps.setString(3, STU_1_ID);
            ps.setString(4, "INT-PG-SCH2");
            ps.setString(5, "RM-PG-SCH2");
            ps.setObject(6, overlapStart);
            ps.setObject(7, overlapEnd);

            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "PostgreSQL GiST constraint no_overlapping_student_sessions must reject overlapping student appointments across drives.");
            assertEquals("23P01", ex.getSQLState(), "Expected SQLSTATE 23P01 (exclusion_violation)");
            assertTrue(ex.getMessage().contains("no_overlapping_student_sessions"),
                    "Error must reference constraint no_overlapping_student_sessions");
        } catch (SQLException e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("PostgreSQL Half-Open Interval: Boundary-touching appointments [10:00, 10:45) and [10:45, 11:30) succeed cleanly")
    void testBoundaryTouchingAppointments_succeedCleanly() {
        LocalDateTime start1 = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0);
        LocalDateTime end1 = start1.plusMinutes(45); // 10:45
        LocalDateTime start2 = end1; // 10:45 exactly touches end of session 1
        LocalDateTime end2 = start2.plusMinutes(45); // 11:30

        InterviewSession session1 = new InterviewSession("SES-BND-1", APP_1_ID, INT_1_ID, ROOM_1_ID, start1, end1);
        sessionRepository.add(session1);

        // Different interviewer and student, but SAME room touching boundary exactly at 10:45
        interviewerRepository.add(new Interviewer("INT-PG-SCH2", "Claude Shannon", CMP_ID));
        InterviewSession session2 = new InterviewSession("SES-BND-2", APP_2_ID, "INT-PG-SCH2", ROOM_1_ID, start2, end2);

        // Should succeed without PostgreSQL GiST exclusion violation because interval is [) (half-open)
        assertDoesNotThrow(() -> sessionRepository.add(session2),
                "PostgreSQL half-open tsrange [) must allow boundary-touching appointments without conflict.");
        assertEquals(2, fixtureSessionCount());
    }

    @Test
    @DisplayName("PostgreSQL Check Constraint: Session with start_time >= end_time violates chk_session_time_interval (23514)")
    void testInvalidTimeInterval_violatesCheckConstraint() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0);
        LocalDateTime end = start.minusMinutes(30); // end is BEFORE start

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, "SES-CHK-INV");
            ps.setString(2, APP_1_ID);
            ps.setString(3, STU_1_ID);
            ps.setString(4, INT_1_ID);
            ps.setString(5, ROOM_1_ID);
            ps.setObject(6, start);
            ps.setObject(7, end);

            SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                    "PostgreSQL check constraint chk_session_time_interval must reject sessions where end_time <= start_time.");
            assertEquals("23514", ex.getSQLState(), "Expected SQLSTATE 23514 (check_violation)");
            assertTrue(ex.getMessage().contains("chk_session_time_interval"),
                    "Error must reference constraint chk_session_time_interval");
        } catch (SQLException e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Partial Success Batch: First session commits and second session fails with conflict without cascading rollback")
    void testPartialSuccessBatch_preservesCommittedAndRejectsConflicted() {
        LocalDateTime start1 = LocalDateTime.now().plusDays(6).withHour(9).withMinute(0);
        LocalDateTime end1 = start1.plusMinutes(45);

        // Proposed session 1: valid slot for APP_1_ID
        InterviewSession session1 = new InterviewSession("SES-PARTIAL-1", APP_1_ID, INT_1_ID, ROOM_1_ID, start1, end1);

        // Proposed session 2: APP_2_ID contending for SAME room and time (conflict!)
        InterviewSession session2 = new InterviewSession("SES-PARTIAL-2", APP_2_ID, INT_1_ID, ROOM_1_ID, start1, end1);

        InterviewService.PublicationResult result = interviewService.publishSchedule(
                List.of(session1, session2), Duration.ofMinutes(15));

        // Outcome must be partial success
        assertEquals(1, result.publishedCount(), "Exactly 1 session must commit.");
        assertEquals(1, result.conflictRejections().size(), "Exactly 1 session must be rejected with conflict.");
        assertEquals("SES-PARTIAL-1", result.publishedSessions().get(0).getId());

        // Database state verification
        assertEquals(1, fixtureSessionCount(), "Committed session must persist in database.");
        assertTrue(sessionRepository.existsById("SES-PARTIAL-1"));
        assertFalse(sessionRepository.existsById("SES-PARTIAL-2"));

        // Application status verification: APP_1 transitioned to SCHEDULED, APP_2 remains SHORTLISTED
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepository.findById(APP_1_ID).get().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_2_ID).get().getStatus());

        // Event listener verification: Exactly 1 event emitted for committed session 1, zero for rejected session 2
        assertEquals(1, recordedEvents.size());
        assertEquals(HireFlowEventType.INTERVIEW_SCHEDULED, recordedEvents.get(0).type());
        assertEquals("SES-PARTIAL-1", recordedEvents.get(0).sourceId());
    }

    @Test
    @DisplayName("Token Replay After Partial Success: Cache rejects re-execution with PreviewConsumedException")
    void testTokenReplayAfterPartialSuccess_rejectedWithNoSideEffects() {
        String previewId = "PREV-PARTIAL-REPLAY-" + UUID.randomUUID().toString().substring(0, 8);
        SchedulingPreviewState state = new SchedulingPreviewState(
                previewId, "officer_test",
                java.time.Instant.now(), java.time.Instant.now().plus(SchedulingPreviewState.DEFAULT_MAX_AGE),
                false, DRV_ID, "Job", "Company",
                LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0),
                45, 15, 2, 2, 0,
                List.of(), List.of()
        );
        previewCacheService.put(state);

        // Consume token first time
        SchedulingPreviewState consumedState = previewCacheService.consume(previewId, "officer_test");
        assertNotNull(consumedState);

        // Second consumption attempt must be rejected
        assertThrows(com.hireflow.api.exception.PreviewConsumedException.class,
                () -> previewCacheService.consume(previewId, "officer_test"),
                "Replaying a consumed preview token must be unconditionally rejected.");
    }

    @Test
    @DisplayName("JVM Restart Recovery: Database remains authoritative and queryable after cache is cleared")
    void testJvmRestartRecovery_databaseRemainsAuthoritative() {
        LocalDateTime start = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0);
        LocalDateTime end = start.plusMinutes(45);
        InterviewSession committed = new InterviewSession("SES-PERSIST-1", APP_1_ID, INT_1_ID, ROOM_1_ID, start, end);
        sessionRepository.add(committed);
        applicationRepository.updateStatusConditional(APP_1_ID, ApplicationStatus.SHORTLISTED, ApplicationStatus.SCHEDULED, "Confirmed");

        // Simulate complete JVM restart / crash by clearing cache service
        previewCacheService.clear();

        // Database remains fully authoritative
        assertTrue(sessionRepository.existsById("SES-PERSIST-1"));
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepository.findById(APP_1_ID).get().getStatus());

        List<InterviewSession> sessions = sessionRepository.findByApplicationId(APP_1_ID);
        assertEquals(1, sessions.size());
        assertEquals("SES-PERSIST-1", sessions.get(0).getId());
    }
    /** Count only appointments owned by this class's drives; preserve other runs. */
    private long fixtureSessionCount() {
        return sessionRepository.findAll().stream().filter(session ->
            applicationRepository.findById(session.getApplicationId())
                .map(app -> java.util.Set.of(DRV_ID, "DRV-PG-SCH2").contains(app.getDriveId())).orElse(false)).count();
    }
}
