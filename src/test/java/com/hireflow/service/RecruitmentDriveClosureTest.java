package com.hireflow.service;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecruitmentDriveClosureTest - Stage 3F.1 Manual Closure Semantics and Invariants")
class RecruitmentDriveClosureTest {

    private DriveRepository inMemoryDriveRepo;
    private CompanyRepository companyRepo;
    private ApplicationRepository appRepo;
    private StudentRepository studentRepo;
    private EligibilityService eligibilityService;
    private ApplicationService appService;
    private RecruitmentDriveService driveService;

    @BeforeEach
    void setUp() {
        inMemoryDriveRepo = new InMemoryDriveRepository();
        companyRepo = new InMemoryCompanyRepository();
        appRepo = new InMemoryApplicationRepository();
        studentRepo = new InMemoryStudentRepository();
        eligibilityService = new EligibilityService();
        appService = new ApplicationService(appRepo, studentRepo, inMemoryDriveRepo, eligibilityService);
        driveService = new RecruitmentDriveService(inMemoryDriveRepo, companyRepo, appRepo);

        companyRepo.add(new Company("CMP-GOOGLE", "Google LLC", "Technology"));
        companyRepo.add(new Company("CMP-MSFT", "Microsoft Corp", "Technology"));

        studentRepo.add(new Student("STU-001", "Aarav Sharma", "aarav@univ.edu", "CSE", 9.2, 2026, true));
        studentRepo.add(new Student("STU-002", "Diya Patel", "diya@univ.edu", "CSE", 8.5, 2026, true));
    }

    @Test
    @DisplayName("manual closure preserves original application deadline")
    void testOriginalDeadlinePreservedOnClosure() {
        LocalDate originalDeadline = LocalDate.now().plusDays(14);
        RecruitmentDrive drive = driveService.createDrive("DRV-GGL-01", "CMP-GOOGLE", "Cloud Engineer", 8.0, Set.of("CSE"), originalDeadline);

        assertFalse(drive.isClosed());
        assertEquals(originalDeadline, drive.getApplicationDeadline());
        assertTrue(drive.isOpen(LocalDate.now()));

        RecruitmentDrive closed = driveService.closeDrive("DRV-GGL-01");

        assertTrue(closed.isClosed(), "Drive must be marked closed");
        assertEquals(originalDeadline, closed.getApplicationDeadline(), "Original application deadline must be preserved exactly");
        assertFalse(closed.isOpen(LocalDate.now()), "Closed drive must report isOpen=false");
        assertFalse(closed.isDeadlinePassed(LocalDate.now()), "Deadline itself is in the future, so deadlinePassed must be false");
    }

    @Test
    @DisplayName("closed drive rejects new applications via ApplicationService")
    void testClosedDriveRejectsApplicationSubmission() {
        LocalDate deadline = LocalDate.now().plusDays(7);
        driveService.createDrive("DRV-MSFT-01", "CMP-MSFT", "Backend Dev", 7.5, Set.of("CSE"), deadline);

        // STU-001 can apply before closure
        Application app1 = appService.submitApplication("STU-001", "DRV-MSFT-01");
        assertNotNull(app1);
        assertEquals(ApplicationStatus.APPLIED, app1.getStatus());

        // Placement officer closes drive
        driveService.closeDrive("DRV-MSFT-01");

        // STU-002 tries to apply after closure
        ValidationException ex = assertThrows(ValidationException.class, () ->
                appService.submitApplication("STU-002", "DRV-MSFT-01")
        );
        assertTrue(ex.getMessage().contains("closed for applications"), "Exception must explain drive is closed: " + ex.getMessage());
    }

    @Test
    @DisplayName("expired drive is clearly distinguishable from manually closed drive")
    void testExpiredVsManuallyClosedDistinction() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        // Expired drive (deadline in past, not manually closed)
        RecruitmentDrive expiredDrive = new RecruitmentDrive("DRV-EXP", "CMP-GOOGLE", "Dev 1", 7.0, Set.of("CSE"), yesterday, false);

        // Manually closed drive (deadline in future, isClosed=true)
        RecruitmentDrive manuallyClosedDrive = new RecruitmentDrive("DRV-MAN", "CMP-GOOGLE", "Dev 2", 7.0, Set.of("CSE"), nextWeek, true);

        // Active open drive
        RecruitmentDrive openDrive = new RecruitmentDrive("DRV-OPN", "CMP-GOOGLE", "Dev 3", 7.0, Set.of("CSE"), nextWeek, false);

        // Both are not open
        assertFalse(expiredDrive.isOpen(LocalDate.now()));
        assertFalse(manuallyClosedDrive.isOpen(LocalDate.now()));
        assertTrue(openDrive.isOpen(LocalDate.now()));

        // Distinguishable properties
        assertFalse(expiredDrive.isClosed(), "Expired drive is not manually closed");
        assertTrue(expiredDrive.isDeadlinePassed(LocalDate.now()), "Expired drive deadline has passed");

        assertTrue(manuallyClosedDrive.isClosed(), "Manually closed drive has isClosed=true");
        assertFalse(manuallyClosedDrive.isDeadlinePassed(LocalDate.now()), "Manually closed drive deadline has not passed");
    }

    @Test
    @DisplayName("existing applications are unaffected by drive closure")
    void testExistingApplicationsUnaffectedByClosure() {
        LocalDate deadline = LocalDate.now().plusDays(10);
        driveService.createDrive("DRV-SAFE", "CMP-GOOGLE", "Site Reliability Eng", 7.0, Set.of("CSE"), deadline);

        Application app = appService.submitApplication("STU-001", "DRV-SAFE");
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Close drive
        driveService.closeDrive("DRV-SAFE");

        // Existing application remains intact and can transition through recruitment lifecycle
        Application retrieved = appService.getApplicationOrThrow(app.getId());
        assertEquals(ApplicationStatus.APPLIED, retrieved.getStatus());

        Application shortlisted = appService.shortlistApplication(app.getId());
        assertEquals(ApplicationStatus.SHORTLISTED, shortlisted.getStatus());
    }

    @Test
    @DisplayName("findActiveDrives excludes both expired and manually closed drives")
    void testFindActiveDrivesFiltering() {
        LocalDate today = LocalDate.now();
        RecruitmentDrive open = inMemoryDriveRepo.add(new RecruitmentDrive("D1", "CMP-GOOGLE", "Role 1", 7.0, Set.of("CSE"), today.plusDays(5), false));
        RecruitmentDrive closed = inMemoryDriveRepo.add(new RecruitmentDrive("D2", "CMP-GOOGLE", "Role 2", 7.0, Set.of("CSE"), today.plusDays(5), true));
        RecruitmentDrive expired = inMemoryDriveRepo.add(new RecruitmentDrive("D3", "CMP-GOOGLE", "Role 3", 7.0, Set.of("CSE"), today.minusDays(2), false));

        List<RecruitmentDrive> active = inMemoryDriveRepo.findActiveDrives(today);
        assertEquals(1, active.size());
        assertEquals("D1", active.get(0).getId());
    }
}
