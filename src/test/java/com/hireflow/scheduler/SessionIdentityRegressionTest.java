package com.hireflow.scheduler;

import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Interviewer;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Room;
import com.hireflow.model.SchedulingRequest;
import com.hireflow.model.SchedulingResult;
import com.hireflow.model.TimeWindow;
import com.hireflow.repository.InMemoryApplicationRepository;
import com.hireflow.repository.InMemoryDriveRepository;
import com.hireflow.repository.InMemoryInterviewSessionRepository;
import com.hireflow.repository.InMemoryStudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.InterviewService;
import com.hireflow.service.InterviewService.PublicationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase C Regression Test Suite: Deterministic Scheduling IDs.
 * Verifies:
 *  1. Stable ID for the same application across repeated previews.
 *  2. Distinct IDs for distinct applications.
 *  3. Valid database column length (<= 32 chars) and hexadecimal format.
 *  4. Successful publication using generated UUID v3 IDs into confirmed sessions.
 *  5. Duplicate / replay handling during publication.
 *  6. Existing confirmed session behavior (conflict avoidance & skipping).
 *  7. Unchanged slot/interviewer/room allocations for representative inputs.
 */
class SessionIdentityRegressionTest {

    private SchedulingRequest createSingleRequest(String applicationId) {
        return SchedulingRequest.builder()
                .applications(List.of(new Application(applicationId, "STU-ID", "DRV-ID", ApplicationStatus.SHORTLISTED)))
                .interviewers(List.of(new Interviewer("INT-ID", "Fictional Interviewer", "CMP-ID")))
                .rooms(List.of(new Room("RM-ID", "Fictional Room", 1)))
                .schedulingWindow(new TimeWindow(LocalDateTime.of(2027, 1, 1, 9, 0), LocalDateTime.of(2027, 1, 1, 12, 0)))
                .interviewDuration(Duration.ofMinutes(30))
                .bufferDuration(Duration.ofMinutes(10))
                .addDriveCompany("DRV-ID", "CMP-ID")
                .build();
    }

    private SchedulingRequest createMultiRequest(List<Application> apps, List<Interviewer> interviewers, List<Room> rooms) {
        SchedulingRequest.Builder builder = SchedulingRequest.builder()
                .applications(apps)
                .interviewers(interviewers)
                .rooms(rooms)
                .schedulingWindow(new TimeWindow(LocalDateTime.of(2027, 1, 1, 9, 0), LocalDateTime.of(2027, 1, 1, 12, 0)))
                .interviewDuration(Duration.ofMinutes(45))
                .bufferDuration(Duration.ofMinutes(15));
        for (Interviewer i : interviewers) {
            builder.addDriveCompany("DRV-01", i.getCompanyId());
        }
        return builder.build();
    }

    @Test
    @DisplayName("1. Stable ID for the same application across repeated previews")
    void stableIdForSameApplicationAcrossRepeatedPreviews() {
        var scheduler = new GreedyScheduler();
        var preview1 = scheduler.schedule(createSingleRequest("APP-STABLE-001")).getScheduledSessions().getFirst();
        var preview2 = scheduler.schedule(createSingleRequest("APP-STABLE-001")).getScheduledSessions().getFirst();
        var preview3 = scheduler.schedule(createSingleRequest("APP-STABLE-001")).getScheduledSessions().getFirst();

        assertNotNull(preview1.getId());
        assertEquals(preview1.getId(), preview2.getId(), "Proposed ID must be identical across multiple preview passes");
        assertEquals(preview1.getId(), preview3.getId(), "Proposed ID must remain stable across repeated invocations");
    }

    @Test
    @DisplayName("2. Distinct IDs for distinct applications")
    void distinctIdsForDistinctApplications() {
        var scheduler = new GreedyScheduler();
        var sessionA = scheduler.schedule(createSingleRequest("APP-001")).getScheduledSessions().getFirst();
        var sessionB = scheduler.schedule(createSingleRequest("APP-002")).getScheduledSessions().getFirst();
        var sessionC = scheduler.schedule(createSingleRequest("APP-003")).getScheduledSessions().getFirst();

        assertNotEquals(sessionA.getId(), sessionB.getId());
        assertNotEquals(sessionB.getId(), sessionC.getId());
        assertNotEquals(sessionA.getId(), sessionC.getId());
    }

    @Test
    @DisplayName("3. Valid database column length and hex format conforming to VARCHAR(32)")
    void validDatabaseColumnLengthAndHexFormat() {
        var scheduler = new GreedyScheduler();
        var session = scheduler.schedule(createSingleRequest("APP-COL-LEN-TEST-12345")).getScheduledSessions().getFirst();

        String id = session.getId();
        assertNotNull(id);
        assertEquals(32, id.length(), "UUID v3 hex string without hyphens must be exactly 32 chars");
        assertTrue(id.length() <= 32, "Must satisfy PostgreSQL VARCHAR(32) column constraint");
        assertTrue(id.matches("^[A-F0-9]{32}$"), "ID must be uppercase hexadecimal");
    }

    @Test
    @DisplayName("4. Successful publication using generated IDs into repository")
    void successfulPublicationUsingGeneratedIds() {
        var sessionRepo = new InMemoryInterviewSessionRepository();
        var appRepo = new InMemoryApplicationRepository();
        var studentRepo = new InMemoryStudentRepository();
        var driveRepo = new InMemoryDriveRepository();
        var eligService = new EligibilityService();
        var appService = new ApplicationService(appRepo, studentRepo, driveRepo, eligService);
        var interviewService = new InterviewService(sessionRepo, appRepo, appService);

        // Seed application in SHORTLISTED state
        Application app = new Application("APP-PUB-01", "STU-01", "DRV-01", ApplicationStatus.SHORTLISTED);
        appRepo.save(app);

        var scheduler = new GreedyScheduler();
        SchedulingResult schedResult = scheduler.schedule(createSingleRequest("APP-PUB-01"));
        assertEquals(1, schedResult.getScheduledSessions().size());
        InterviewSession proposed = schedResult.getScheduledSessions().getFirst();

        PublicationResult pubResult = interviewService.publishSchedule(List.of(proposed), Duration.ofMinutes(10));
        assertEquals(1, pubResult.publishedCount(), "Expected single session to be published successfully");
        assertEquals(0, pubResult.skippedDuplicateApplicationIds().size());
        assertEquals(0, pubResult.conflictRejections().size());

        // Verify stored in sessionRepository with exact generated ID
        assertTrue(sessionRepo.existsById(proposed.getId()));
        var storedOpt = sessionRepo.findById(proposed.getId());
        assertTrue(storedOpt.isPresent());
        assertEquals(proposed.getId(), storedOpt.get().getId());
        assertEquals("APP-PUB-01", storedOpt.get().getApplicationId());

        // Verify application status updated to SCHEDULED
        var updatedApp = appRepo.findById("APP-PUB-01");
        assertTrue(updatedApp.isPresent());
        assertEquals(ApplicationStatus.SCHEDULED, updatedApp.get().getStatus());
    }

    @Test
    @DisplayName("5. Duplicate and replay handling during publication")
    void duplicateAndReplayHandlingDuringPublication() {
        var sessionRepo = new InMemoryInterviewSessionRepository();
        var appRepo = new InMemoryApplicationRepository();
        var studentRepo = new InMemoryStudentRepository();
        var driveRepo = new InMemoryDriveRepository();
        var eligService = new EligibilityService();
        var appService = new ApplicationService(appRepo, studentRepo, driveRepo, eligService);
        var interviewService = new InterviewService(sessionRepo, appRepo, appService);

        Application app = new Application("APP-REPLAY-01", "STU-01", "DRV-01", ApplicationStatus.SHORTLISTED);
        appRepo.save(app);

        var scheduler = new GreedyScheduler();
        InterviewSession proposed = scheduler.schedule(createSingleRequest("APP-REPLAY-01")).getScheduledSessions().getFirst();

        // First publication: succeeds
        PublicationResult firstPub = interviewService.publishSchedule(List.of(proposed), Duration.ofMinutes(10));
        assertEquals(1, firstPub.publishedCount());

        // Replay of same publication: skipped as duplicate
        PublicationResult replayPub = interviewService.publishSchedule(List.of(proposed), Duration.ofMinutes(10));
        assertEquals(0, replayPub.publishedCount(), "Replay must not create additional published sessions");
        assertEquals(1, replayPub.skippedDuplicateApplicationIds().size(), "Replay must be recorded in skippedDuplicates");
        assertEquals("APP-REPLAY-01", replayPub.skippedDuplicateApplicationIds().getFirst());
    }

    @Test
    @DisplayName("6. Existing confirmed session behavior prevents duplicate scheduling")
    void existingConfirmedSessionBehavior() {
        var sessionRepo = new InMemoryInterviewSessionRepository();
        var appRepo = new InMemoryApplicationRepository();
        var studentRepo = new InMemoryStudentRepository();
        var driveRepo = new InMemoryDriveRepository();
        var eligService = new EligibilityService();
        var appService = new ApplicationService(appRepo, studentRepo, driveRepo, eligService);
        var interviewService = new InterviewService(sessionRepo, appRepo, appService);

        // Application already scheduled in prior drive
        Application app = new Application("APP-EXISTING-01", "STU-01", "DRV-01", ApplicationStatus.SCHEDULED);
        appRepo.save(app);
        InterviewSession existing = new InterviewSession(
                "PRE-EXISTING-SESS-ID",
                "APP-EXISTING-01",
                "INT-ID",
                "RM-ID",
                LocalDateTime.of(2027, 1, 1, 9, 0),
                LocalDateTime.of(2027, 1, 1, 9, 30)
        );
        sessionRepo.save(existing);

        var scheduler = new GreedyScheduler();
        InterviewSession proposedNew = scheduler.schedule(createSingleRequest("APP-EXISTING-01")).getScheduledSessions().getFirst();

        PublicationResult result = interviewService.publishSchedule(List.of(proposedNew), Duration.ofMinutes(10));
        assertEquals(0, result.publishedCount());
        assertEquals(1, result.skippedDuplicateApplicationIds().size(), "Existing confirmed session prevents duplicate appointment");
    }

    @Test
    @DisplayName("7. Unchanged slot/interviewer/room allocations for representative inputs")
    void unchangedSlotInterviewerRoomAllocationsForRepresentativeInputs() {
        var scheduler = new GreedyScheduler();

        Application app1 = new Application("APP-REP-01", "STU-01", "DRV-01", ApplicationStatus.SHORTLISTED);
        Application app2 = new Application("APP-REP-02", "STU-02", "DRV-01", ApplicationStatus.SHORTLISTED);
        Interviewer int1 = new Interviewer("INT-01", "Alice Engineer", "CMP-01");
        Interviewer int2 = new Interviewer("INT-02", "Bob Architect", "CMP-01");
        Room rm1 = new Room("RM-01", "Boardroom A", 1);
        Room rm2 = new Room("RM-02", "Interview Pod B", 2);

        SchedulingRequest request = createMultiRequest(List.of(app1, app2), List.of(int1, int2), List.of(rm1, rm2));
        SchedulingResult result = scheduler.schedule(request);

        assertEquals(2, result.getScheduledSessions().size());
        assertEquals(0, result.getUnscheduledApplications().size());

        InterviewSession s1 = result.getScheduledSessions().get(0);
        InterviewSession s2 = result.getScheduledSessions().get(1);

        // App1 gets earliest slot 09:00 - 09:45 with first available interviewer and room
        assertEquals("APP-REP-01", s1.getApplicationId());
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), s1.getStartTime());
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 45), s1.getEndTime());
        assertEquals("INT-01", s1.getInterviewerId());
        assertEquals("RM-01", s1.getRoomId());

        // App2 can run concurrently at 09:00 with second interviewer & room
        assertEquals("APP-REP-02", s2.getApplicationId());
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), s2.getStartTime());
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 45), s2.getEndTime());
        assertEquals("INT-02", s2.getInterviewerId());
        assertEquals("RM-02", s2.getRoomId());

        // Check ID properties on both
        assertEquals(32, s1.getId().length());
        assertEquals(32, s2.getId().length());
        assertNotEquals(s1.getId(), s2.getId());
    }
}
