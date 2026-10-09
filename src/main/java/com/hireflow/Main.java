package com.hireflow;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.*;
import com.hireflow.ui.MainFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HireFlow Stage 1 Application Entry Point.
 * Launches the Swing Desktop Interface on graphical environments,
 * or runs the comprehensive console demonstration when run in headless mode or with --demo.
 */
public class Main {

    public static void main(String[] args) {
        // Check for explicit CLI demo flag
        boolean forceCli = false;
        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg) || "--cli".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg)) {
                forceCli = true;
                break;
            }
        }

        if (forceCli || GraphicsEnvironment.isHeadless()) {
            if (GraphicsEnvironment.isHeadless()) {
                System.out.println("[HireFlow] Headless environment detected. Executing Console Demonstration Mode...\n");
            }
            runConsoleDemo();
            return;
        }

        // Launch Swing Desktop Interface on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fallback to default Swing Look and Feel
            }

            // 1. Initialize Repositories
            StudentRepository studentRepo = new InMemoryStudentRepository();
            CompanyRepository companyRepo = new InMemoryCompanyRepository();
            DriveRepository driveRepo = new InMemoryDriveRepository();
            ApplicationRepository appRepo = new InMemoryApplicationRepository();
            InterviewerRepository interviewerRepo = new InMemoryInterviewerRepository();
            RoomRepository roomRepo = new InMemoryRoomRepository();
            InterviewSessionRepository sessionRepo = new InMemoryInterviewSessionRepository();

            // 2. Initialize Services
            StudentService studentService = new StudentService(studentRepo, appRepo);
            RecruitmentDriveService driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
            EligibilityService eligibilityService = new EligibilityService();
            ApplicationService applicationService = new ApplicationService(appRepo, studentRepo, driveRepo, eligibilityService, sessionRepo);
            SchedulingEngine scheduler = new GreedyScheduler();

            // 3. Seed Standard Sample Data
            MainFrame.seedSampleData(
                    studentRepo, companyRepo, driveRepo,
                    appRepo, interviewerRepo, roomRepo, sessionRepo,
                    applicationService
            );

            // 4. Construct and display MainFrame
            MainFrame frame = new MainFrame(
                    studentRepo, companyRepo, driveRepo,
                    appRepo, interviewerRepo, roomRepo, sessionRepo,
                    studentService, driveService, eligibilityService,
                    applicationService, scheduler
            );
            frame.setVisible(true);
        });
    }

    /**
     * Executes the comprehensive end-to-end console demonstration of all services and scheduling logic.
     */
    public static void runConsoleDemo() {
        System.out.println("===============================================================================");
        System.out.println("                     HIREFLOW - STAGE 1 COMPREHENSIVE DEMO                    ");
        System.out.println("          Campus Recruitment and Automated Placement Management System        ");
        System.out.println("===============================================================================\n");

        // 1. Initialize Repositories
        StudentRepository studentRepo = new InMemoryStudentRepository();
        CompanyRepository companyRepo = new InMemoryCompanyRepository();
        DriveRepository driveRepo = new InMemoryDriveRepository();
        ApplicationRepository appRepo = new InMemoryApplicationRepository();
        InterviewerRepository interviewerRepo = new InMemoryInterviewerRepository();
        RoomRepository roomRepo = new InMemoryRoomRepository();
        InterviewSessionRepository sessionRepo = new InMemoryInterviewSessionRepository();

        // 2. Initialize Services
        StudentService studentService = new StudentService(studentRepo, appRepo);
        RecruitmentDriveService driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
        EligibilityService eligibilityService = new EligibilityService();
        ApplicationService applicationService = new ApplicationService(appRepo, studentRepo, driveRepo, eligibilityService, sessionRepo);
        SchedulingEngine scheduler = new GreedyScheduler();

        // 3. Populate Sample Data
        System.out.println("[*] Seeding sample data (11 Students, 4 Companies, 5 Recruitment Drives)...");
        SampleDataLoader seeder = new SampleDataLoader(studentRepo, companyRepo, driveRepo, interviewerRepo, roomRepo);
        seeder.seedAll();

        System.out.println("    Loaded " + studentService.getTotalStudentsCount() + " students, " +
                driveService.getTotalDrivesCount() + " drives, " +
                interviewerRepo.count() + " interviewers, and " +
                roomRepo.count() + " rooms.\n");

        // ---------------------------------------------------------------------
        // DEMO STEP 1: List Recruitment Drives
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 1: Available Recruitment Drives");
        System.out.println("-------------------------------------------------------------------------------");
        List<RecruitmentDrive> drives = driveService.getAllDrives();
        System.out.printf("%-10s | %-16s | %-32s | %-8s | %-16s | %-12s%n",
                "Drive ID", "Company", "Job Title", "Min CGPA", "Branches", "Deadline");
        System.out.println("-----------+------------------+----------------------------------+----------+------------------+-------------");

        for (RecruitmentDrive drive : drives) {
            String companyName = companyRepo.findById(drive.getCompanyId())
                    .map(Company::getName).orElse(drive.getCompanyId());
            System.out.printf("%-10s | %-16s | %-32s | %-8.2f | %-16s | %-12s%n",
                    drive.getId(),
                    companyName,
                    drive.getJobTitle(),
                    drive.getMinimumCgpa(),
                    drive.getEligibleBranches(),
                    drive.getApplicationDeadline());
        }
        System.out.println();

        // ---------------------------------------------------------------------
        // DEMO STEP 2: Check Student Eligibility
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 2: Student Eligibility Evaluation");
        System.out.println("-------------------------------------------------------------------------------");

        Student alice = studentService.getStudentOrThrow("STU-101");
        RecruitmentDrive googleDrive = driveService.getDriveOrThrow("DRV-101");

        System.out.println("Evaluating Candidate: " + alice.getName() + " (" + alice.getId() + ")");
        System.out.println("  - Branch: " + alice.getBranch() + ", CGPA: " + alice.getCgpa() + ", Active: " + alice.isActive());
        System.out.println("Target Drive: " + googleDrive.getJobTitle() + " (" + googleDrive.getId() + ")");
        System.out.println("  - Permitted Branches: " + googleDrive.getEligibleBranches() + ", Cutoff CGPA: " + googleDrive.getMinimumCgpa());

        EligibilityService.EligibilityResult resultAlice = eligibilityService.checkEligibility(alice, googleDrive);
        System.out.println(">> Result: " + (resultAlice.isEligible() ? "[PASS] ELIGIBLE" : "[FAIL] INELIGIBLE"));
        System.out.println("   Reason: " + resultAlice.getFormattedReason() + "\n");

        // ---------------------------------------------------------------------
        // DEMO STEP 3: Submit an Eligible Application
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 3: Submitting an Eligible Application");
        System.out.println("-------------------------------------------------------------------------------");

        Application appAliceGoogle = applicationService.submitApplication("STU-101", "DRV-101");
        System.out.println("[SUCCESS] Application successfully submitted and registered!");
        System.out.println("  - Application ID : " + appAliceGoogle.getId());
        System.out.println("  - Student ID     : " + appAliceGoogle.getStudentId() + " (" + alice.getName() + ")");
        System.out.println("  - Drive ID       : " + appAliceGoogle.getDriveId());
        System.out.println("  - Status         : " + appAliceGoogle.getStatus());
        System.out.println("  - Remarks        : " + appAliceGoogle.getRemarks() + "\n");

        // ---------------------------------------------------------------------
        // DEMO STEP 4: Reject Ineligible Applications
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 4: Rejecting Ineligible Applications");
        System.out.println("-------------------------------------------------------------------------------");

        // Case 4A: CGPA below cutoff
        Student hannah = studentService.getStudentOrThrow("STU-108");
        System.out.println("[Scenario 4A] Testing CGPA Cutoff Violation:");
        System.out.println("  Candidate: " + hannah.getName() + " (CGPA " + hannah.getCgpa() + ") -> Drive Cutoff: " + googleDrive.getMinimumCgpa());
        try {
            applicationService.submitApplication(hannah.getId(), googleDrive.getId());
            System.err.println("  [ERROR] Ineligible application was unexpectedly accepted!");
        } catch (ValidationException ex) {
            System.out.println("  [REJECTED AS EXPECTED] Exception caught:");
            System.out.println("  >> " + ex.getMessage());
        }

        // Case 4B: Branch mismatch
        Student fiona = studentService.getStudentOrThrow("STU-106");
        System.out.println("\n[Scenario 4B] Testing Branch Authorization Violation:");
        System.out.println("  Candidate: " + fiona.getName() + " (Branch " + fiona.getBranch() + ") -> Allowed Branches: " + googleDrive.getEligibleBranches());
        try {
            applicationService.submitApplication(fiona.getId(), googleDrive.getId());
            System.err.println("  [ERROR] Ineligible application was unexpectedly accepted!");
        } catch (ValidationException ex) {
            System.out.println("  [REJECTED AS EXPECTED] Exception caught:");
            System.out.println("  >> " + ex.getMessage());
        }

        // Case 4C: Application Deadline Expired
        RecruitmentDrive expiredDrive = driveService.getDriveOrThrow("DRV-105");
        System.out.println("\n[Scenario 4C] Testing Expired Drive Deadline Violation:");
        System.out.println("  Candidate: " + alice.getName() + " -> Expired Drive: " + expiredDrive.getId() + " (Deadline: " + expiredDrive.getApplicationDeadline() + ")");
        try {
            applicationService.submitApplication(alice.getId(), expiredDrive.getId());
            System.err.println("  [ERROR] Expired drive application was unexpectedly accepted!");
        } catch (ValidationException ex) {
            System.out.println("  [REJECTED AS EXPECTED] Exception caught:");
            System.out.println("  >> " + ex.getMessage());
        }
        System.out.println();

        // ---------------------------------------------------------------------
        // DEMO STEP 5: Reject Duplicate Application
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 5: Rejecting Duplicate Application");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("Attempting second application submission for Student 'STU-101' to Drive 'DRV-101'...");
        try {
            applicationService.submitApplication("STU-101", "DRV-101");
            System.err.println("  [ERROR] Duplicate application was unexpectedly accepted!");
        } catch (DuplicateEntityException ex) {
            System.out.println("  [REJECTED AS EXPECTED] Duplicate Entity Exception caught:");
            System.out.println("  >> " + ex.getMessage());
        }
        System.out.println();

        // ---------------------------------------------------------------------
        // DEMO STEP 6: Automated Interview Scheduling Engine
        // ---------------------------------------------------------------------
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("DEMO STEP 6: Automated Greedy Interview Scheduling Engine");
        System.out.println("-------------------------------------------------------------------------------");

        // Submit and shortlist additional applications
        Application appBobGoogle = applicationService.submitApplication("STU-102", "DRV-101");
        Application appDianaMs = applicationService.submitApplication("STU-104", "DRV-102");
        Application appAliceMs = applicationService.submitApplication("STU-101", "DRV-102"); // Alice shortlisted by 2 companies!
        Application appIanMs = applicationService.submitApplication("STU-109", "DRV-102");

        // Transition candidates to SHORTLISTED status
        applicationService.shortlistApplication(appAliceGoogle.getId());
        applicationService.shortlistApplication(appBobGoogle.getId());
        applicationService.shortlistApplication(appDianaMs.getId());
        applicationService.shortlistApplication(appAliceMs.getId());
        applicationService.shortlistApplication(appIanMs.getId());

        // Also add an unshortlisted application to prove it is rejected by the scheduler
        Application unshortlistedApp = applicationService.submitApplication("STU-103", "DRV-102");

        // Set up pre-existing confirmed appointment (Hard constraint 7)
        LocalDateTime scheduleBase = LocalDateTime.of(LocalDate.now(), java.time.LocalTime.of(9, 0));
        InterviewSession confirmedSession = new InterviewSession(
                "SES-CONFIRMED-001",
                "APP-PRIOR-099",
                "INT-001",
                "RM-101",
                scheduleBase,
                scheduleBase.plusMinutes(45)
        );

        TimeWindow window = new TimeWindow(scheduleBase, scheduleBase.plusHours(3)); // 09:00 -> 12:00
        Duration duration = Duration.ofMinutes(45);
        Duration buffer = Duration.ofMinutes(15);

        System.out.println("[*] Scheduling Parameters:");
        System.out.println("    Window: " + window);
        System.out.println("    Duration: " + duration.toMinutes() + " mins, Buffer: " + buffer.toMinutes() + " mins");
        System.out.println("    Pre-existing Confirmed Appointment in RM-101: 09:00 - 09:45 (INT-001)");
        System.out.println("    Total Candidate Applications Submitted: 6 (5 Shortlisted, 1 Applied)\n");

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(List.of(
                        appAliceGoogle, appBobGoogle, appDianaMs,
                        appAliceMs, appIanMs, unshortlistedApp
                ))
                .interviewers(interviewerRepo.findAll())
                .rooms(roomRepo.findAll())
                .schedulingWindow(window)
                .interviewDuration(duration)
                .bufferDuration(buffer)
                .existingSessions(List.of(confirmedSession))
                .addDriveCompany("DRV-101", "CMP-001")
                .addDriveCompany("DRV-102", "CMP-002")
                .build();

        SchedulingResult schedulingResult = scheduler.schedule(request);

        System.out.println(">> " + schedulingResult.getSummary() + "\n");
        System.out.println("PROPOSED INTERVIEW SCHEDULE TIMETABLE:");
        System.out.printf("%-14s | %-10s | %-16s | %-18s | %-12s | %-25s%n",
                "Session ID", "App ID", "Student ID", "Interviewer", "Room", "Time Window");
        System.out.println("---------------+------------+------------------+--------------------+--------------+--------------------------");

        for (InterviewSession session : schedulingResult.getScheduledSessions()) {
            String studentId = appRepo.findById(session.getApplicationId()).map(Application::getStudentId).orElse("?");
            String studentName = studentRepo.findById(studentId).map(Student::getName).orElse(studentId);
            String interviewerName = interviewerRepo.findById(session.getInterviewerId()).map(Interviewer::getName).orElse(session.getInterviewerId());
            String roomName = roomRepo.findById(session.getRoomId()).map(Room::getName).orElse(session.getRoomId());

            System.out.printf("%-14s | %-10s | %-16s | %-18s | %-12s | %s -> %s%n",
                    session.getId(),
                    session.getApplicationId(),
                    studentName,
                    interviewerName,
                    roomName,
                    session.getStartTime().toLocalTime(),
                    session.getEndTime().toLocalTime());
        }

        if (!schedulingResult.getUnscheduledApplications().isEmpty()) {
            System.out.println("\nUNSCHEDULED APPLICATIONS & REASONS:");
            for (UnscheduledApplication un : schedulingResult.getUnscheduledApplications()) {
                System.out.println("  - Application " + un.getApplicationId() + " (" + un.getStudentId() + "):");
                System.out.println("    Reason: " + un.reason());
            }
        }

        System.out.println("\n===============================================================================");
        System.out.println("                 STAGE 1 ENGINE & SERVICES DEMO COMPLETED                      ");
        System.out.println("===============================================================================");
    }
}
