package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.dto.StudentApplicationViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.CsrfTokenManager;
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

@DisplayName("StudentApplicationController - Application Tracking & Submission Tests")
class StudentApplicationControllerTest {

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private ApplicationService applicationService;
    private CompanyRepository companyRepository;

    private StudentApplicationController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        studentService = mock(StudentService.class);
        driveService = mock(RecruitmentDriveService.class);
        applicationService = mock(ApplicationService.class);
        companyRepository = mock(CompanyRepository.class);

        controller = new StudentApplicationController(studentService, driveService, applicationService, companyRepository);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(request.getContextPath()).thenReturn("/hireflow");
    }

    @Test
    @DisplayName("tracking: loads only the authenticated student's applications")
    void testTrackingLoadsStudentApplications() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        Student student = new Student("STU-101", "Rahul Sharma", "rahul@example.com", "CSE", 8.85, 2026);

        RecruitmentDrive drive1 = new RecruitmentDrive("DRV-001", "CMP-001", "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        Company comp1 = new Company("CMP-001", "Tech Corp", "tech.com");
        Application app1 = new Application("APP-001", "STU-101", "DRV-001", ApplicationStatus.APPLIED);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(studentService.getStudentById("STU-101")).thenReturn(Optional.of(student));
        when(applicationService.getApplicationsForStudent("STU-101")).thenReturn(List.of(app1));
        when(driveService.getAllDrives()).thenReturn(List.of(drive1));
        when(companyRepository.findAll()).thenReturn(List.of(comp1));

        controller.doGet(request, response);

        ArgumentCaptor<List<StudentApplicationViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("applicationItems"), captor.capture());

        List<StudentApplicationViewItem> items = captor.getValue();
        assertEquals(1, items.size());
        assertEquals("APP-001", items.get(0).application().getId());
        assertEquals("Tech Corp", items.get(0).companyName());

        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("submission: succeeds with valid CSRF token and employs PRG redirect")
    void testSubmitApplicationSucceedsWithPrg() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        String csrfToken = "valid_csrf_token_hex_1234567890abcdef";

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(csrfToken);
        when(request.getParameter("csrfToken")).thenReturn(csrfToken);
        when(request.getParameter("driveId")).thenReturn("DRV-001");

        Application createdApp = new Application("APP-999", "STU-101", "DRV-001", ApplicationStatus.APPLIED);
        when(applicationService.submitApplication("STU-101", "DRV-001")).thenReturn(createdApp);

        controller.doPost(request, response);

        verify(applicationService).submitApplication("STU-101", "DRV-001");
        verify(session).setAttribute(eq("flashType"), eq("success"));
        verify(response).sendRedirect("/hireflow/student/applications");
    }

    @Test
    @DisplayName("submission: ignores any client-supplied 'studentId' form field and uses principal's ID")
    void testSubmitApplicationIgnoresClientStudentId() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        String csrfToken = "valid_csrf_token_hex_1234567890abcdef";

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(csrfToken);
        when(request.getParameter("csrfToken")).thenReturn(csrfToken);
        when(request.getParameter("driveId")).thenReturn("DRV-001");
        // Attacker injects a different studentId parameter into POST payload
        when(request.getParameter("studentId")).thenReturn("STU-VICTIM");

        Application createdApp = new Application("APP-999", "STU-101", "DRV-001", ApplicationStatus.APPLIED);
        when(applicationService.submitApplication("STU-101", "DRV-001")).thenReturn(createdApp);

        controller.doPost(request, response);

        // Verify application was submitted with STU-101, NOT STU-VICTIM
        verify(applicationService).submitApplication("STU-101", "DRV-001");
        verify(applicationService, never()).submitApplication(eq("STU-VICTIM"), anyString());
    }

    @Test
    @DisplayName("submission: rejects missing or invalid CSRF token with 403 Forbidden")
    void testSubmitApplicationRejectsInvalidCsrf() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("correct_token");
        when(request.getParameter("csrfToken")).thenReturn("wrong_token");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));
        verify(applicationService, never()).submitApplication(anyString(), anyString());
    }

    @Test
    @DisplayName("submission: handles DuplicateEntityException gracefully with warning message")
    void testSubmitApplicationHandlesDuplicateGracefully() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        String csrfToken = "valid_csrf_token_hex_1234567890abcdef";

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(csrfToken);
        when(request.getParameter("csrfToken")).thenReturn(csrfToken);
        when(request.getParameter("driveId")).thenReturn("DRV-001");

        when(applicationService.submitApplication("STU-101", "DRV-001"))
                .thenThrow(new DuplicateEntityException("Already applied"));

        controller.doPost(request, response);

        verify(session).setAttribute(eq("flashType"), eq("warn"));
        verify(response).sendRedirect("/hireflow/student/drives");
    }

    @Test
    @DisplayName("submission: handles ValidationException gracefully with error message")
    void testSubmitApplicationHandlesValidationExceptionGracefully() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        String csrfToken = "valid_csrf_token_hex_1234567890abcdef";

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(csrfToken);
        when(request.getParameter("csrfToken")).thenReturn(csrfToken);
        when(request.getParameter("driveId")).thenReturn("DRV-001");

        when(applicationService.submitApplication("STU-101", "DRV-001"))
                .thenThrow(new ValidationException("CGPA cutoff violation"));

        controller.doPost(request, response);

        verify(session).setAttribute(eq("flashType"), eq("error"));
        verify(response).sendRedirect("/hireflow/student/drives");
    }
}
