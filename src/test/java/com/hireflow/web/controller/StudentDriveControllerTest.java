package com.hireflow.web.controller;

import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.dto.StudentDriveViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("StudentDriveController - Drives Directory & Eligibility Tests")
class StudentDriveControllerTest {

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private EligibilityService eligibilityService;
    private ApplicationService applicationService;
    private CompanyRepository companyRepository;

    private StudentDriveController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        studentService = mock(StudentService.class);
        driveService = mock(RecruitmentDriveService.class);
        eligibilityService = mock(EligibilityService.class);
        applicationService = mock(ApplicationService.class);
        companyRepository = mock(CompanyRepository.class);

        controller = new StudentDriveController(studentService, driveService, eligibilityService, applicationService, companyRepository);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(request.getContextPath()).thenReturn("/hireflow");
    }

    @Test
    @DisplayName("drives: evaluates server-side eligibility and marks existing applications")
    void testDrivesListingWithEligibilityAndApplications() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        Student student = new Student("STU-101", "Rahul Sharma", "rahul@example.com", "CSE", 8.85, 2026);

        RecruitmentDrive drive1 = new RecruitmentDrive("DRV-001", "CMP-001", "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        RecruitmentDrive drive2 = new RecruitmentDrive("DRV-002", "CMP-002", "Hardware Engineer", 7.5, Set.of("ECE"), LocalDate.now().plusDays(10));

        Company comp1 = new Company("CMP-001", "Tech Corp", "tech.com");
        Company comp2 = new Company("CMP-002", "Circuits Inc", "circuits.com");

        Application app1 = new Application("APP-001", "STU-101", "DRV-001", ApplicationStatus.APPLIED);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(studentService.getStudentById("STU-101")).thenReturn(Optional.of(student));
        when(driveService.getAllDrives()).thenReturn(List.of(drive1, drive2));
        when(applicationService.getApplicationsForStudent("STU-101")).thenReturn(List.of(app1));
        when(companyRepository.findAll()).thenReturn(List.of(comp1, comp2));

        when(eligibilityService.checkEligibility(student, drive1))
                .thenReturn(EligibilityService.EligibilityResult.eligible(student, drive1));
        when(eligibilityService.checkEligibility(student, drive2))
                .thenReturn(EligibilityService.EligibilityResult.ineligible(List.of("Branch mismatch"), student, drive2));

        controller.doGet(request, response);

        ArgumentCaptor<List<StudentDriveViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("driveItems"), captor.capture());

        List<StudentDriveViewItem> items = captor.getValue();
        assertEquals(2, items.size());

        StudentDriveViewItem item1 = items.stream().filter(it -> it.drive().getId().equals("DRV-001")).findFirst().orElseThrow();
        assertTrue(item1.hasApplied(), "Drive 1 should be marked as already applied");
        assertTrue(item1.eligibilityResult().isEligible());

        StudentDriveViewItem item2 = items.stream().filter(it -> it.drive().getId().equals("DRV-002")).findFirst().orElseThrow();
        assertFalse(item2.hasApplied());
        assertFalse(item2.eligibilityResult().isEligible(), "Drive 2 should be marked ineligible due to branch");

        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("drives: rejects non-student principal with 403 Forbidden")
    void testDrivesRejectsNonStudent() throws Exception {
        UserPrincipal officerPrincipal = new UserPrincipal("officer", UserRole.PLACEMENT_OFFICER, "Officer", null);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(dispatcher, never()).forward(request, response);
    }
}
