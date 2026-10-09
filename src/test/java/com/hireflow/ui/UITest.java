package com.hireflow.ui;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HireFlow Stage 1 UI Components & Integration Tests")
class UITest {

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
    private GreedyScheduler scheduler;

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
        scheduler = new GreedyScheduler();

        MainFrame.seedSampleData(
                studentRepo, companyRepo, driveRepo,
                appRepo, interviewerRepo, roomRepo, sessionRepo,
                applicationService
        );
    }

    @Test
    @DisplayName("Sample data loader populates all required entities")
    void testSampleDataSeeding() {
        assertTrue(studentService.getTotalStudentsCount() >= 10, "Should have at least 10 students seeded");
        assertTrue(companyRepo.count() >= 3, "Should have at least 3 companies seeded");
        assertTrue(driveService.getAllDrives().size() >= 4, "Should have at least 4 drives seeded");
        assertTrue(interviewerRepo.count() >= 2, "Should have interviewers seeded");
        assertTrue(roomRepo.count() >= 2, "Should have rooms seeded");
        assertTrue(applicationService.getAllApplications().size() >= 5, "Should have sample applications seeded");
    }

    @Test
    @DisplayName("UI panels can be instantiated and initialized without throwing exceptions")
    void testPanelInitialization() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless environment detected; skipping Swing window render checks.");
            return;
        }

        DashboardPanel dashboard = new DashboardPanel(
                studentService, driveService, companyRepo, applicationService, sessionRepo, s -> {}
        );
        assertNotNull(dashboard);

        StudentPanel studentPanel = new StudentPanel(studentService, () -> {});
        assertNotNull(studentPanel);

        CompanyDrivePanel companyDrivePanel = new CompanyDrivePanel(driveService, companyRepo, () -> {});
        assertNotNull(companyDrivePanel);

        ApplicationPanel applicationPanel = new ApplicationPanel(
                applicationService, eligibilityService, studentRepo, driveRepo, companyRepo, () -> {}
        );
        assertNotNull(applicationPanel);

        SchedulePanel schedulePanel = new SchedulePanel(
                scheduler, applicationService, studentRepo, driveRepo, companyRepo,
                interviewerRepo, roomRepo, sessionRepo, () -> {}
        );
        assertNotNull(schedulePanel);

        MainFrame mainFrame = new MainFrame(
                studentRepo, companyRepo, driveRepo, appRepo,
                interviewerRepo, roomRepo, sessionRepo,
                studentService, driveService, eligibilityService,
                applicationService, scheduler
        );
        assertNotNull(mainFrame);
        mainFrame.refreshAllPanels();
        mainFrame.dispose();
    }
}
