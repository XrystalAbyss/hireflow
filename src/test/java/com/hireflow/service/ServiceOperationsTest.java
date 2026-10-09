package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HireFlow Business Services Tests")
public class ServiceOperationsTest {

    private StudentRepository studentRepo;
    private CompanyRepository companyRepo;
    private DriveRepository driveRepo;
    private ApplicationRepository appRepo;

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private EligibilityService eligibilityService;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        studentRepo = new InMemoryStudentRepository();
        companyRepo = new InMemoryCompanyRepository();
        driveRepo = new InMemoryDriveRepository();
        appRepo = new InMemoryApplicationRepository();

        studentService = new StudentService(studentRepo, appRepo);
        driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
        eligibilityService = new EligibilityService();
        applicationService = new ApplicationService(appRepo, studentRepo, driveRepo, eligibilityService);
    }

    @Nested
    @DisplayName("EligibilityService Boundary & Rule Tests")
    class EligibilityServiceTests {

        @Test
        @DisplayName("Exact CGPA boundary match is eligible")
        void testExactCgpaBoundaryMatch() {
            Student s = new Student("S1", "Alice", "alice@example.com", "CSE", 8.00, 2026);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 8.00, Set.of("CSE"), LocalDate.now().plusDays(5));

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d);
            assertTrue(result.isEligible());
            assertTrue(result.getFormattedReason().contains("satisfies all"));
        }

        @Test
        @DisplayName("CGPA 0.01 below cutoff is ineligible")
        void testCgpaJustBelowCutoff() {
            Student s = new Student("S1", "Alice", "alice@example.com", "CSE", 7.99, 2026);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 8.00, Set.of("CSE"), LocalDate.now().plusDays(5));

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d);
            assertFalse(result.isEligible());
            assertTrue(result.getFormattedReason().contains("minimum cutoff"));
        }

        @Test
        @DisplayName("Deadline on current date is eligible")
        void testDeadlineTodayEligible() {
            LocalDate today = LocalDate.of(2026, 10, 9);
            Student s = new Student("S1", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 8.0, Set.of("CSE"), today);

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d, today);
            assertTrue(result.isEligible());
        }

        @Test
        @DisplayName("Deadline passed yesterday is ineligible")
        void testDeadlinePassedIneligible() {
            LocalDate today = LocalDate.of(2026, 10, 9);
            LocalDate deadlineYesterday = today.minusDays(1);

            Student s = new Student("S1", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 8.0, Set.of("CSE"), deadlineYesterday);

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d, today);
            assertFalse(result.isEligible());
            assertTrue(result.getFormattedReason().contains("Application deadline"));
        }

        @Test
        @DisplayName("Inactive student is rejected even with top CGPA")
        void testInactiveStudentIneligible() {
            Student s = new Student("S1", "Alice", "alice@example.com", "CSE", 9.9, 2026, false);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 7.0, Set.of("CSE"), LocalDate.now().plusDays(5));

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d);
            assertFalse(result.isEligible());
            assertTrue(result.getFormattedReason().contains("inactive"));
        }

        @Test
        @DisplayName("Multiple violations are all recorded in reasons")
        void testMultipleViolationsCollected() {
            LocalDate today = LocalDate.of(2026, 10, 9);
            Student s = new Student("S1", "Alice", "alice@example.com", "MECH", 6.5, 2026, false);
            RecruitmentDrive d = new RecruitmentDrive("D1", "C1", "Dev", 8.0, Set.of("CSE"), today.minusDays(2));

            EligibilityService.EligibilityResult result = eligibilityService.checkEligibility(s, d, today);
            assertFalse(result.isEligible());
            assertEquals(4, result.reasons().size()); // Inactive, CGPA, Branch, Deadline
        }
    }

    @Nested
    @DisplayName("StudentService Operation & Duplicate Tests")
    class StudentServiceTests {

        @Test
        @DisplayName("Register student success and retrieval")
        void testRegisterStudentSuccess() {
            Student s = studentService.registerStudent("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            assertNotNull(s);
            assertEquals(1, studentService.getTotalStudentsCount());
            assertEquals("Alice", studentService.getStudentOrThrow("STU-001").getName());
        }

        @Test
        @DisplayName("Duplicate ID throws DuplicateEntityException")
        void testDuplicateIdThrows() {
            studentService.registerStudent("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            assertThrows(DuplicateEntityException.class, () ->
                    studentService.registerStudent("STU-001", "Bob", "bob@example.com", "ECE", 8.0, 2026)
            );
        }

        @Test
        @DisplayName("Duplicate email throws DuplicateEntityException")
        void testDuplicateEmailThrows() {
            studentService.registerStudent("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            assertThrows(DuplicateEntityException.class, () ->
                    studentService.registerStudent("STU-002", "Bob", "alice@example.com", "ECE", 8.0, 2026)
            );
        }

        @Test
        @DisplayName("Search finds matching students across attributes")
        void testSearchStudents() {
            studentService.registerStudent("STU-001", "Alice Smith", "alice@example.com", "CSE", 8.5, 2026);
            studentService.registerStudent("STU-002", "Bob Smith", "bob@example.com", "ECE", 8.0, 2026);
            studentService.registerStudent("STU-003", "Charlie Day", "charlie@example.com", "MECH", 7.5, 2026);

            List<Student> results = studentService.searchStudents("Smith");
            assertEquals(2, results.size());

            List<Student> mechResults = studentService.searchStudents("mech");
            assertEquals(1, mechResults.size());
            assertEquals("STU-003", mechResults.get(0).getId());
        }
    }

    @Nested
    @DisplayName("RecruitmentDriveService Operation Tests")
    class DriveServiceTests {

        @BeforeEach
        void initCompany() {
            companyRepo.save(new Company("CMP-001", "Google", "Tech"));
        }

        @Test
        @DisplayName("Create drive for valid company succeeds")
        void testCreateDriveSuccess() {
            RecruitmentDrive d = driveService.createDrive("DRV-001", "CMP-001", "SDE", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
            assertNotNull(d);
            assertEquals(1, driveService.getTotalDrivesCount());
        }

        @Test
        @DisplayName("Create drive for non-existent company throws EntityNotFoundException")
        void testCreateDriveNonExistentCompanyThrows() {
            assertThrows(EntityNotFoundException.class, () ->
                    driveService.createDrive("DRV-001", "NON-EXISTENT", "SDE", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10))
            );
        }

        @Test
        @DisplayName("Create duplicate drive ID throws DuplicateEntityException")
        void testDuplicateDriveIdThrows() {
            driveService.createDrive("DRV-001", "CMP-001", "SDE", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
            assertThrows(DuplicateEntityException.class, () ->
                    driveService.createDrive("DRV-001", "CMP-001", "QA", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10))
            );
        }
    }

    @Nested
    @DisplayName("ApplicationService Operation & Status Transition Tests")
    class ApplicationServiceTests {

        private Student eligibleStudent;
        private Student ineligibleStudent;
        private RecruitmentDrive activeDrive;

        @BeforeEach
        void initTestData() {
            companyRepo.save(new Company("CMP-001", "Google", "Tech"));

            eligibleStudent = studentRepo.save(new Student("STU-001", "Alice", "alice@example.com", "CSE", 9.0, 2026, true));
            ineligibleStudent = studentRepo.save(new Student("STU-002", "Hannah", "hannah@example.com", "CIVIL", 6.5, 2026, true));

            activeDrive = driveRepo.save(new RecruitmentDrive(
                    "DRV-001", "CMP-001", "SDE", 8.0, Set.of("CSE", "IT"), LocalDate.now().plusDays(10)
            ));
        }

        @Test
        @DisplayName("Eligible application submission succeeds with APPLIED status")
        void testSubmitEligibleApplicationSuccess() {
            Application app = applicationService.submitApplication("STU-001", "DRV-001");
            assertNotNull(app);
            assertEquals("STU-001", app.getStudentId());
            assertEquals("DRV-001", app.getDriveId());
            assertEquals(ApplicationStatus.APPLIED, app.getStatus());
        }

        @Test
        @DisplayName("Ineligible application is rejected and throws ValidationException")
        void testSubmitIneligibleApplicationRejected() {
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    applicationService.submitApplication("STU-002", "DRV-001")
            );
            assertTrue(ex.getMessage().contains("Application rejected"));
        }

        @Test
        @DisplayName("Duplicate application submission throws DuplicateEntityException")
        void testDuplicateApplicationRejected() {
            applicationService.submitApplication("STU-001", "DRV-001");
            DuplicateEntityException ex = assertThrows(DuplicateEntityException.class, () ->
                    applicationService.submitApplication("STU-001", "DRV-001")
            );
            assertTrue(ex.getMessage().contains("already applied"));
        }

        @Test
        @DisplayName("Non-existent student or drive throws EntityNotFoundException")
        void testNonExistentEntitiesThrow() {
            assertThrows(EntityNotFoundException.class, () ->
                    applicationService.submitApplication("NON-STUDENT", "DRV-001")
            );
            assertThrows(EntityNotFoundException.class, () ->
                    applicationService.submitApplication("STU-001", "NON-DRIVE")
            );
        }

        @Test
        @DisplayName("Valid status lifecycle: APPLIED -> SHORTLISTED -> SCHEDULED -> SELECTED")
        void testValidStatusLifecycle() {
            Application app = applicationService.submitApplication("STU-001", "DRV-001");
            assertEquals(ApplicationStatus.APPLIED, app.getStatus());

            applicationService.updateStatus(app.getId(), ApplicationStatus.SHORTLISTED, "Shortlisted");
            assertEquals(ApplicationStatus.SHORTLISTED, applicationService.getApplicationOrThrow(app.getId()).getStatus());

            applicationService.updateStatus(app.getId(), ApplicationStatus.SCHEDULED, "Scheduled");
            assertEquals(ApplicationStatus.SCHEDULED, applicationService.getApplicationOrThrow(app.getId()).getStatus());

            applicationService.updateStatus(app.getId(), ApplicationStatus.SELECTED, "Offer extended");
            assertEquals(ApplicationStatus.SELECTED, applicationService.getApplicationOrThrow(app.getId()).getStatus());
        }

        @Test
        @DisplayName("Invalid status transition throws ValidationException")
        void testInvalidStatusTransitionThrows() {
            Application app = applicationService.submitApplication("STU-001", "DRV-001");

            // Direct jump from APPLIED to SELECTED is illegal
            assertThrows(ValidationException.class, () ->
                    applicationService.updateStatus(app.getId(), ApplicationStatus.SELECTED, "Direct jump")
            );

            // Transition to REJECTED is valid
            applicationService.updateStatus(app.getId(), ApplicationStatus.REJECTED, "Not moving forward");

            // Moving from terminal REJECTED back to SCHEDULED is illegal
            assertThrows(ValidationException.class, () ->
                    applicationService.updateStatus(app.getId(), ApplicationStatus.SCHEDULED, "Unreject")
            );
        }
    }
}
