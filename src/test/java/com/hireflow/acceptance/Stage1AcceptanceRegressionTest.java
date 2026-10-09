package com.hireflow.acceptance;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 1 Production-Style Acceptance & Regression Tests")
class Stage1AcceptanceRegressionTest {

    private StudentRepository studentRepo;
    private CompanyRepository companyRepo;
    private DriveRepository driveRepo;
    private ApplicationRepository appRepo;
    private InterviewerRepository interviewerRepo;
    private RoomRepository roomRepo;
    private InterviewSessionRepository sessionRepo;

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private EligibilityService eligibilityService;
    private ApplicationService applicationService;
    private InterviewService interviewService;
    private SchedulingEngine schedulingEngine;

    @BeforeEach
    void setUp() {
        studentRepo = new InMemoryStudentRepository();
        companyRepo = new InMemoryCompanyRepository();
        driveRepo = new InMemoryDriveRepository();
        appRepo = new InMemoryApplicationRepository();
        interviewerRepo = new InMemoryInterviewerRepository();
        roomRepo = new InMemoryRoomRepository();
        sessionRepo = new InMemoryInterviewSessionRepository();

        studentService = new StudentService(studentRepo, appRepo);
        driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
        eligibilityService = new EligibilityService();
        applicationService = new ApplicationService(appRepo, studentRepo, driveRepo, eligibilityService, sessionRepo);
        interviewService = new InterviewService(sessionRepo, appRepo, applicationService);
        schedulingEngine = new GreedyScheduler();

        // Seed base setup
        companyRepo.save(new Company("CMP-001", "Google Cloud", "Cloud Computing"));
        companyRepo.save(new Company("CMP-002", "Microsoft", "Enterprise"));

        studentRepo.save(new Student("STU-101", "Alice Smith", "alice@univ.edu", "CSE", 9.20, 2026, true));
        studentRepo.save(new Student("STU-102", "Bob Jones", "bob@univ.edu", "CSE", 8.40, 2026, true));
        studentRepo.save(new Student("STU-103", "Charlie Brown", "charlie@univ.edu", "IT", 7.80, 2026, true));

        driveRepo.save(new RecruitmentDrive("DRV-101", "CMP-001", "SDE-1", 8.00, Set.of("CSE", "IT"), LocalDate.now().plusDays(10)));
        driveRepo.save(new RecruitmentDrive("DRV-102", "CMP-002", "Cloud Engineer", 7.50, Set.of("CSE", "IT"), LocalDate.now().plusDays(10)));

        interviewerRepo.add(new Interviewer("INT-001", "Dr. Sarah Chen", "CMP-001"));
        interviewerRepo.add(new Interviewer("INT-002", "Marcus Vance", "CMP-002"));

        roomRepo.add(new Room("RM-101", "Tech Lab Booth 1", 2));
        roomRepo.add(new Room("RM-102", "Tech Lab Booth 2", 2));
    }

    @Test
    @DisplayName("Acceptance 1: Publishing a schedule updates application statuses consistently to SCHEDULED")
    void testPublishScheduleUpdatesApplicationStatusesConsistently() {
        Application a1 = applicationService.submitApplication("STU-101", "DRV-101");
        Application a2 = applicationService.submitApplication("STU-102", "DRV-101");
        applicationService.shortlistApplication(a1.getId());
        applicationService.shortlistApplication(a2.getId());

        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        InterviewSession s1 = new InterviewSession("SES-001", a1.getId(), "INT-001", "RM-101", start, start.plusMinutes(45));
        InterviewSession s2 = new InterviewSession("SES-002", a2.getId(), "INT-001", "RM-101", start.plusMinutes(60), start.plusMinutes(105));

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s1, s2), Duration.ofMinutes(15));

        assertEquals(2, result.publishedCount());
        assertEquals(0, result.conflictRejections().size());
        assertEquals(0, result.skippedDuplicateApplicationIds().size());

        // Verify status in applicationService & repository
        assertEquals(ApplicationStatus.SCHEDULED, applicationService.getApplicationOrThrow(a1.getId()).getStatus());
        assertEquals(ApplicationStatus.SCHEDULED, applicationService.getApplicationOrThrow(a2.getId()).getStatus());

        // Verify sessions stored
        assertEquals(2, sessionRepo.count());
        assertTrue(sessionRepo.existsById("SES-001"));
        assertTrue(sessionRepo.existsById("SES-002"));
    }

    @Test
    @DisplayName("Acceptance 2: Publishing the same schedule twice cannot create duplicate appointments")
    void testPublishSameScheduleTwiceDoesNotCreateDuplicateAppointments() {
        Application a1 = applicationService.submitApplication("STU-101", "DRV-101");
        applicationService.shortlistApplication(a1.getId());

        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        InterviewSession s1 = new InterviewSession("SES-001", a1.getId(), "INT-001", "RM-101", start, start.plusMinutes(45));

        // First publication
        InterviewService.PublicationResult firstResult = interviewService.publishSchedule(List.of(s1), Duration.ofMinutes(15));
        assertEquals(1, firstResult.publishedCount());
        assertEquals(1, sessionRepo.count());

        // Second publication with identical session
        InterviewService.PublicationResult secondResult = interviewService.publishSchedule(List.of(s1), Duration.ofMinutes(15));
        assertEquals(0, secondResult.publishedCount(), "Second publication must not publish any new sessions");
        assertEquals(1, secondResult.skippedDuplicateApplicationIds().size(), "Must detect duplicate application");
        assertEquals(1, sessionRepo.count(), "Repository session count must remain 1");

        // Attempt second publication with a different session ID for the SAME application
        InterviewSession s1Duplicate = new InterviewSession("SES-099", a1.getId(), "INT-001", "RM-101", start.plusHours(2), start.plusHours(2).plusMinutes(45));
        InterviewService.PublicationResult thirdResult = interviewService.publishSchedule(List.of(s1Duplicate), Duration.ofMinutes(15));
        assertEquals(0, thirdResult.publishedCount(), "Must reject new session ID for already scheduled application");
        assertEquals(1, thirdResult.skippedDuplicateApplicationIds().size());
        assertEquals(1, sessionRepo.count());
    }

    @Test
    @DisplayName("Acceptance 3: Schedule publication revalidates conflicts against existing confirmed sessions")
    void testPublishScheduleRevalidatesConflictsAgainstConfirmedSessions() {
        Application a1 = applicationService.submitApplication("STU-101", "DRV-101");
        Application a2 = applicationService.submitApplication("STU-102", "DRV-101");
        applicationService.shortlistApplication(a1.getId());
        applicationService.shortlistApplication(a2.getId());

        LocalDateTime time = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));

        // Session 1 confirmed in Room RM-101 with INT-001
        InterviewSession s1 = new InterviewSession("SES-001", a1.getId(), "INT-001", "RM-101", time, time.plusMinutes(45));
        interviewService.publishSchedule(List.of(s1), Duration.ofMinutes(15));

        // Proposed Session 2 attempting to book the SAME room at an overlapping time (10:15 - 11:00)
        InterviewSession s2Conflicting = new InterviewSession(
                "SES-002", a2.getId(), "INT-002", "RM-101", time.plusMinutes(15), time.plusMinutes(60)
        );

        InterviewService.PublicationResult result = interviewService.publishSchedule(List.of(s2Conflicting), Duration.ofMinutes(15));

        assertEquals(0, result.publishedCount(), "Conflicting session must not be published");
        assertEquals(1, result.conflictRejections().size(), "Must report conflict rejection");
        assertTrue(result.conflictRejections().getFirst().contains("Room conflict"));
        assertEquals(1, sessionRepo.count(), "Repository count must remain 1");

        // Application a2 must remain SHORTLISTED and NOT transitioned to SCHEDULED
        assertEquals(ApplicationStatus.SHORTLISTED, applicationService.getApplicationOrThrow(a2.getId()).getStatus());
    }

    @Test
    @DisplayName("Acceptance 4: Deleting a student with active applications is blocked by referential integrity")
    void testDeletingStudentWithApplicationsIsBlocked() {
        Application app = applicationService.submitApplication("STU-101", "DRV-101");
        assertNotNull(app);

        // Attempt to delete student STU-101
        ValidationException ex = assertThrows(ValidationException.class, () ->
                studentService.deleteStudent("STU-101")
        );
        assertTrue(ex.getMessage().contains("application(s) reference this candidate"));
        assertTrue(studentRepo.existsById("STU-101"), "Student must still exist in repository");
    }

    @Test
    @DisplayName("Acceptance 5: Deleting an unreferenced student succeeds")
    void testDeletingUnreferencedStudentSucceeds() {
        // Charlie (STU-103) has zero applications
        boolean deleted = studentService.deleteStudent("STU-103");
        assertTrue(deleted);
        assertFalse(studentRepo.existsById("STU-103"));
    }

    @Test
    @DisplayName("Acceptance 6: Deleting a recruitment drive with active applications is blocked")
    void testDeletingDriveWithApplicationsIsBlocked() {
        Application app = applicationService.submitApplication("STU-101", "DRV-101");
        assertNotNull(app);

        // Attempt to delete drive DRV-101
        ValidationException ex = assertThrows(ValidationException.class, () ->
                driveService.deleteDrive("DRV-101")
        );
        assertTrue(ex.getMessage().contains("application(s) reference this drive"));
        assertTrue(driveRepo.existsById("DRV-101"), "Drive must still exist in repository");
    }

    @Test
    @DisplayName("Acceptance 7: Deleting an unreferenced drive succeeds")
    void testDeletingUnreferencedDriveSucceeds() {
        // DRV-102 has zero applications
        boolean deleted = driveService.deleteDrive("DRV-102");
        assertTrue(deleted);
        assertFalse(driveRepo.existsById("DRV-102"));
    }

    @Test
    @DisplayName("Acceptance 8: Fictional interviewer identities are verified across all loaded records")
    void testFictionalInterviewerIdentities() {
        for (Interviewer interviewer : interviewerRepo.findAll()) {
            assertFalse(interviewer.getName().toLowerCase().contains("sundar"), "Must not use real executive name");
            assertFalse(interviewer.getName().toLowerCase().contains("pichai"), "Must not use real executive name");
            assertFalse(interviewer.getName().toLowerCase().contains("satya"), "Must not use real executive name");
            assertFalse(interviewer.getName().toLowerCase().contains("nadella"), "Must not use real executive name");
            assertFalse(interviewer.getName().toLowerCase().contains("solomon"), "Must not use real executive name");
        }
    }

    @Test
    @DisplayName("Acceptance 9: Deleting an application with confirmed interview sessions is blocked by referential integrity")
    void testDeleteApplicationWithConfirmedSessionIsBlocked() {
        Application a1 = applicationService.submitApplication("STU-101", "DRV-101");
        applicationService.shortlistApplication(a1.getId());

        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        InterviewSession s1 = new InterviewSession("SES-001", a1.getId(), "INT-001", "RM-101", start, start.plusMinutes(45));
        interviewService.publishSchedule(List.of(s1), Duration.ofMinutes(15));

        // Attempt to delete application a1
        ValidationException ex = assertThrows(ValidationException.class, () ->
                applicationService.deleteApplication(a1.getId())
        );
        assertTrue(ex.getMessage().contains("confirmed interview session(s) reference it") || ex.getMessage().contains("SCHEDULED status"));
        assertTrue(appRepo.existsById(a1.getId()), "Application must still exist in repository");
    }

    @Test
    @DisplayName("Acceptance 10: Deleting an unreferenced application succeeds")
    void testDeleteUnreferencedApplicationSucceeds() {
        Application a = applicationService.submitApplication("STU-101", "DRV-101");
        assertTrue(appRepo.existsById(a.getId()));

        boolean deleted = applicationService.deleteApplication(a.getId());
        assertTrue(deleted);
        assertFalse(appRepo.existsById(a.getId()));
    }

    @Test
    @DisplayName("Acceptance 11: Candidate deletion without ApplicationRepository configured fails defensively")
    void testDeletingStudentWithoutAppRepoThrowsDefensively() {
        StudentService standaloneStudentService = new StudentService(studentRepo);
        assertThrows(IllegalStateException.class, () ->
                standaloneStudentService.deleteStudent("STU-101")
        );
    }

    @Test
    @DisplayName("Acceptance 12: Drive deletion without ApplicationRepository configured fails defensively")
    void testDeletingDriveWithoutAppRepoThrowsDefensively() {
        RecruitmentDriveService standaloneDriveService = new RecruitmentDriveService(driveRepo, companyRepo);
        assertThrows(IllegalStateException.class, () ->
                standaloneDriveService.deleteDrive("DRV-101")
        );
    }
}
