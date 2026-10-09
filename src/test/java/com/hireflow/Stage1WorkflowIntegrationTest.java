package com.hireflow;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.*;
import com.hireflow.ui.MainFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit verification suite covering all 10 required Stage 1 workflows:
 * 1. Add student and retrieve record
 * 2. Create recruitment drive
 * 3. Evaluate eligibility
 * 4. Submit valid application
 * 5. Reject invalid & duplicate applications
 * 6. Shortlist applicant
 * 7. Generate proposed schedule
 * 8. Verify no student/interviewer/room overlaps
 * 9. Display unscheduled applications & reasons
 * 10. Navigate all Swing screens without crashes
 */
@DisplayName("Stage 1 - Complete 10 Workflow Verification Suite")
public class Stage1WorkflowIntegrationTest {

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
        schedulingEngine = new GreedyScheduler();

        MainFrame.seedSampleData(
                studentRepo, companyRepo, driveRepo,
                appRepo, interviewerRepo, roomRepo, sessionRepo,
                applicationService
        );
    }

    @Test
    @DisplayName("Workflow 1: Add a student and retrieve the same record")
    void testWorkflow01_AddStudentAndRetrieveRecord() {
        String studentId = "STU-999";
        Student added = studentService.registerStudent(
                studentId, "Grace Hopper", "grace.hopper@univ.edu", "CSE", 9.85, 2026, true
        );

        assertNotNull(added, "Registered student should not be null");
        assertEquals("STU-999", added.getId());

        // Retrieve record via service
        Student retrieved = studentService.getStudentOrThrow(studentId);
        assertEquals("Grace Hopper", retrieved.getName());
        assertEquals("grace.hopper@univ.edu", retrieved.getEmail());
        assertEquals("CSE", retrieved.getBranch());
        assertEquals(9.85, retrieved.getCgpa(), 0.001);
        assertEquals(2026, retrieved.getGraduationYear());
        assertTrue(retrieved.isActive());

        // Retrieve directly from repository
        Optional<Student> fromRepo = studentRepo.findById(studentId);
        assertTrue(fromRepo.isPresent());
        assertEquals(retrieved, fromRepo.get());
    }

    @Test
    @DisplayName("Workflow 2: Create a recruitment drive")
    void testWorkflow02_CreateRecruitmentDrive() {
        String companyId = "CMP-001"; // Google Cloud
        String driveId = "DRV-999";
        LocalDate deadline = LocalDate.now().plusDays(25);

        RecruitmentDrive drive = driveService.createDrive(
                driveId, companyId, "Principal Systems Architect", 8.75, Set.of("CSE", "IT"), deadline
        );

        assertNotNull(drive);
        assertEquals("DRV-999", drive.getId());
        assertEquals(companyId, drive.getCompanyId());
        assertEquals("Principal Systems Architect", drive.getJobTitle());
        assertEquals(8.75, drive.getMinimumCgpa(), 0.001);
        assertEquals(Set.of("CSE", "IT"), drive.getEligibleBranches());
        assertEquals(deadline, drive.getApplicationDeadline());

        // Retrieve and verify existence
        RecruitmentDrive fetched = driveService.getDriveOrThrow(driveId);
        assertEquals(drive, fetched);
        assertTrue(driveService.getActiveDrives().contains(fetched));
    }

    @Test
    @DisplayName("Workflow 3: Evaluate eligibility across all criteria")
    void testWorkflow03_EvaluateEligibility() {
        Student alice = studentService.getStudentOrThrow("STU-101"); // CGPA 9.20, CSE, Active
        RecruitmentDrive googleDrive = driveService.getDriveOrThrow("DRV-101"); // Cutoff 8.00, [CSE, IT]

        // 3a. Eligible candidate
        EligibilityService.EligibilityResult eligibleResult = eligibilityService.checkEligibility(alice, googleDrive);
        assertTrue(eligibleResult.isEligible(), "Alice should be eligible for Google SDE-1");
        assertFalse(eligibleResult.reasons().isEmpty());

        // 3b. Below CGPA cutoff
        Student hannah = studentService.getStudentOrThrow("STU-108"); // CGPA 6.80, CSE
        EligibilityService.EligibilityResult cgpaResult = eligibilityService.checkEligibility(hannah, googleDrive);
        assertFalse(cgpaResult.isEligible());
        assertTrue(cgpaResult.getFormattedReason().toLowerCase().contains("cgpa"));

        // 3c. Department / Branch mismatch
        Student fiona = studentService.getStudentOrThrow("STU-106"); // MECH, CGPA 8.10
        EligibilityService.EligibilityResult branchResult = eligibilityService.checkEligibility(fiona, googleDrive);
        assertFalse(branchResult.isEligible());
        assertTrue(branchResult.getFormattedReason().toLowerCase().contains("branch"));

        // 3d. Expired deadline
        RecruitmentDrive expiredDrive = driveService.getDriveOrThrow("DRV-105"); // Deadline passed
        EligibilityService.EligibilityResult deadlineResult = eligibilityService.checkEligibility(alice, expiredDrive);
        assertFalse(deadlineResult.isEligible());
        assertTrue(deadlineResult.getFormattedReason().toLowerCase().contains("deadline"));

        // 3e. Inactive student
        Student julia = studentService.getStudentOrThrow("STU-110"); // Inactive
        EligibilityService.EligibilityResult activeResult = eligibilityService.checkEligibility(julia, googleDrive);
        assertFalse(activeResult.isEligible());
        assertTrue(activeResult.getFormattedReason().toLowerCase().contains("inactive"));
    }

    @Test
    @DisplayName("Workflow 4: Submit a valid application")
    void testWorkflow04_SubmitValidApplication() {
        Student ethan = studentService.getStudentOrThrow("STU-105"); // ECE, CGPA 7.20
        RecruitmentDrive tata = driveService.getDriveOrThrow("DRV-104"); // Cutoff 7.00, [MECH, CIVIL, ECE]

        Application app = applicationService.submitApplication(ethan.getId(), tata.getId());

        assertNotNull(app);
        assertNotNull(app.getId());
        assertEquals(ethan.getId(), app.getStudentId());
        assertEquals(tata.getId(), app.getDriveId());
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());
        assertNotNull(app.getAppliedAt());

        // Verify retrieval
        Optional<Application> retrieved = applicationService.getApplicationById(app.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(app.getId(), retrieved.get().getId());
    }

    @Test
    @DisplayName("Workflow 5: Reject invalid and duplicate applications")
    void testWorkflow05_RejectInvalidAndDuplicateApplications() {
        // 5a. Reject application due to CGPA cutoff violation
        assertThrows(ValidationException.class, () ->
                applicationService.submitApplication("STU-108", "DRV-101") // Hannah CGPA 6.80 < 8.00
        );

        // 5b. Reject application due to branch mismatch
        assertThrows(ValidationException.class, () ->
                applicationService.submitApplication("STU-106", "DRV-101") // Fiona MECH not in [CSE, IT]
        );

        // 5c. Reject application due to expired deadline
        assertThrows(ValidationException.class, () ->
                applicationService.submitApplication("STU-101", "DRV-105") // Expired
        );

        // 5d. Reject duplicate application
        // In seed data, Alice (STU-101) already applied for DRV-101
        assertThrows(DuplicateEntityException.class, () ->
                applicationService.submitApplication("STU-101", "DRV-101")
        );
    }

    @Test
    @DisplayName("Workflow 6: Shortlist an applicant")
    void testWorkflow06_ShortlistApplicant() {
        // Ethan applies for Tata Motors
        Application app = applicationService.submitApplication("STU-105", "DRV-104");
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Shortlist
        Application shortlisted = applicationService.shortlistApplication(app.getId());
        assertEquals(ApplicationStatus.SHORTLISTED, shortlisted.getStatus());
        assertTrue(shortlisted.getRemarks().contains("shortlisted"));

        // Validate state machine: direct transition from SHORTLISTED to SELECTED without SCHEDULED is illegal
        assertThrows(ValidationException.class, () ->
                applicationService.updateStatus(shortlisted.getId(), ApplicationStatus.SELECTED, "Direct select")
        );
    }

    @Test
    @DisplayName("Workflow 7: Generate a proposed interview schedule")
    void testWorkflow07_GenerateProposedInterviewSchedule() {
        LocalDate today = LocalDate.now();
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(today, LocalTime.of(9, 0)),
                LocalDateTime.of(today, LocalTime.of(13, 0))
        );

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(applicationService.getAllApplications())
                .interviewers(interviewerRepo.findAll())
                .rooms(roomRepo.findAll())
                .schedulingWindow(window)
                .interviewDuration(Duration.ofMinutes(45))
                .bufferDuration(Duration.ofMinutes(15))
                .existingSessions(sessionRepo.findAll())
                .addDriveCompany("DRV-101", "CMP-001")
                .addDriveCompany("DRV-102", "CMP-002")
                .build();

        SchedulingResult result = schedulingEngine.schedule(request);

        assertNotNull(result);
        assertTrue(result.getTotalScheduled() > 0, "Should have scheduled sessions");
        assertFalse(result.getScheduledSessions().isEmpty());

        for (InterviewSession s : result.getScheduledSessions()) {
            assertNotNull(s.getId());
            assertNotNull(s.getApplicationId());
            assertNotNull(s.getInterviewerId());
            assertNotNull(s.getRoomId());
            assertTrue(s.getStartTime().isBefore(s.getEndTime()));
            assertEquals(45, s.getDurationMinutes());
            assertTrue(window.contains(s.getStartTime(), s.getEndTime()));
        }
    }

    @Test
    @DisplayName("Workflow 8: Verify no student, interviewer or room overlaps")
    void testWorkflow08_VerifyNoOverlaps() {
        LocalDate today = LocalDate.now();
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(today, LocalTime.of(9, 0)),
                LocalDateTime.of(today, LocalTime.of(14, 0))
        );
        Duration duration = Duration.ofMinutes(45);
        Duration buffer = Duration.ofMinutes(15);

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(applicationService.getAllApplications())
                .interviewers(interviewerRepo.findAll())
                .rooms(roomRepo.findAll())
                .schedulingWindow(window)
                .interviewDuration(duration)
                .bufferDuration(buffer)
                .existingSessions(sessionRepo.findAll())
                .addDriveCompany("DRV-101", "CMP-001")
                .addDriveCompany("DRV-102", "CMP-002")
                .build();

        SchedulingResult result = schedulingEngine.schedule(request);
        List<InterviewSession> sessions = result.getScheduledSessions();
        ConflictChecker checker = new ConflictChecker();

        Map<String, String> appToStudent = new HashMap<>();
        for (Application app : applicationService.getAllApplications()) {
            appToStudent.put(app.getId().toUpperCase(), app.getStudentId().toUpperCase());
        }

        // Compare every pair of sessions
        for (int i = 0; i < sessions.size(); i++) {
            InterviewSession s1 = sessions.get(i);
            String stu1 = appToStudent.get(s1.getApplicationId().toUpperCase());

            for (int j = i + 1; j < sessions.size(); j++) {
                InterviewSession s2 = sessions.get(j);
                String stu2 = appToStudent.get(s2.getApplicationId().toUpperCase());

                // Constraint 1: Same student must not have overlapping sessions
                if (stu1 != null && stu1.equalsIgnoreCase(stu2)) {
                    assertFalse(checker.intervalsOverlap(s1.getStartTime(), s1.getEndTime(),
                                    s2.getStartTime(), s2.getEndTime()),
                            "Student " + stu1 + " has overlapping interview sessions: " + s1.getId() + " and " + s2.getId());
                }

                // Constraint 2: Same interviewer must not have overlapping sessions (with buffer)
                if (s1.getInterviewerId().equalsIgnoreCase(s2.getInterviewerId())) {
                    assertFalse(checker.intervalsOverlapWithBuffer(s1.getStartTime(), s1.getEndTime(),
                                    s2.getStartTime(), s2.getEndTime(), buffer),
                            "Interviewer " + s1.getInterviewerId() + " has overlapping sessions: " + s1.getId() + " and " + s2.getId());
                }

                // Constraint 3: Same room must not host overlapping sessions (with buffer)
                if (s1.getRoomId().equalsIgnoreCase(s2.getRoomId())) {
                    assertFalse(checker.intervalsOverlapWithBuffer(s1.getStartTime(), s1.getEndTime(),
                                    s2.getStartTime(), s2.getEndTime(), buffer),
                            "Room " + s1.getRoomId() + " has overlapping sessions: " + s1.getId() + " and " + s2.getId());
                }
            }
        }
    }

    @Test
    @DisplayName("Workflow 9: Display unscheduled applications and reasons")
    void testWorkflow09_DisplayUnscheduledApplicationsAndReasons() {
        LocalDate today = LocalDate.now();
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(today, LocalTime.of(9, 0)),
                LocalDateTime.of(today, LocalTime.of(12, 0))
        );

        // Submit an application that stays in APPLIED status (not shortlisted)
        Application unshortlistedApp = applicationService.submitApplication("STU-107", "DRV-104"); // George -> Tata Motors
        assertEquals(ApplicationStatus.APPLIED, unshortlistedApp.getStatus());

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(unshortlistedApp))
                .interviewers(interviewerRepo.findAll())
                .rooms(roomRepo.findAll())
                .schedulingWindow(window)
                .interviewDuration(Duration.ofMinutes(45))
                .bufferDuration(Duration.ofMinutes(15))
                .addDriveCompany("DRV-104", "CMP-004")
                .build();

        SchedulingResult result = schedulingEngine.schedule(request);

        assertEquals(0, result.getTotalScheduled());
        assertEquals(1, result.getTotalUnscheduled());
        assertFalse(result.getUnscheduledApplications().isEmpty());

        UnscheduledApplication unscheduled = result.getUnscheduledApplications().getFirst();
        assertEquals(unshortlistedApp.getId(), unscheduled.getApplicationId());
        assertTrue(unscheduled.reason().contains("SHORTLISTED"), "Reason should specify lack of shortlisted status");
    }

    @Test
    @DisplayName("Workflow 10: Navigate all Swing screens without crashes")
    void testWorkflow10_NavigateAllSwingScreensWithoutCrashes() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless environment: skipping graphical rendering check.");
            return;
        }

        MainFrame mainFrame = new MainFrame(
                studentRepo, companyRepo, driveRepo, appRepo,
                interviewerRepo, roomRepo, sessionRepo,
                studentService, driveService, eligibilityService,
                applicationService, schedulingEngine
        );

        assertNotNull(mainFrame);

        // Switch to all screens sequentially
        assertDoesNotThrow(() -> {
            mainFrame.switchScreen(MainFrame.SCREEN_DASHBOARD);
            mainFrame.switchScreen(MainFrame.SCREEN_STUDENTS);
            mainFrame.switchScreen(MainFrame.SCREEN_DRIVES);
            mainFrame.switchScreen(MainFrame.SCREEN_APPLICATIONS);
            mainFrame.switchScreen(MainFrame.SCREEN_SCHEDULE);
            mainFrame.refreshAllPanels();
        });

        mainFrame.dispose();
    }
}
