package com.hireflow.web.controller;

import com.hireflow.model.*;
import com.hireflow.repository.InMemoryApplicationRepository;
import com.hireflow.repository.InMemoryCompanyRepository;
import com.hireflow.repository.InMemoryDriveRepository;
import com.hireflow.repository.InMemoryStudentRepository;
import com.hireflow.service.*;
import com.hireflow.web.dto.OfficerApplicantViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerApplicationController - Applicant Review and Shortlisting Tests")
class OfficerApplicationControllerTest {

    private InMemoryCompanyRepository companyRepo;
    private InMemoryDriveRepository driveRepo;
    private InMemoryStudentRepository studentRepo;
    private InMemoryApplicationRepository appRepo;

    private CompanyService companyService;
    private RecruitmentDriveService driveService;
    private StudentService studentService;
    private EligibilityService eligibilityService;
    private ApplicationService appService;

    private OfficerApplicationController controller;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal officerPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        driveRepo = new InMemoryDriveRepository();
        studentRepo = new InMemoryStudentRepository();
        appRepo = new InMemoryApplicationRepository();

        companyService = new CompanyService(companyRepo);
        driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
        studentService = new StudentService(studentRepo);
        eligibilityService = new EligibilityService();
        appService = new ApplicationService(appRepo, studentRepo, driveRepo, eligibilityService);

        controller = new OfficerApplicationController(
                appService, driveService, studentService, companyService, eligibilityService
        );

        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        officerPrincipal = new UserPrincipal("officer1", "Officer Name", UserRole.PLACEMENT_OFFICER, null);
        studentPrincipal = new UserPrincipal("student1", "Student Name", UserRole.STUDENT, "STU-001");

        when(req.getContextPath()).thenReturn("/hireflow");
        when(req.getSession(false)).thenReturn(session);
        when(req.getSession(true)).thenReturn(session);
        when(req.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        // Seed domain data
        companyService.registerCompany("CMP-1", "Google", "Tech");
        driveService.createDrive("DRV-1", "CMP-1", "SWE", 7.5, Set.of("CSE", "IT"), LocalDate.now().plusDays(10));
        studentService.registerStudent("STU-1", "Alice Smith", "alice@univ.edu", "CSE", 8.5, 2026);
        studentService.registerStudent("STU-2", "Bob Jones", "bob@univ.edu", "MECH", 6.0, 2026);
    }

    @Test
    @DisplayName("doGet: lists applications with eligibility check for authenticated officer")
    void testApplicationListAllowedForOfficer() throws ServletException, IOException {
        Application app1 = appService.submitApplication("STU-1", "DRV-1");

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-token-xyz");

        controller.doGet(req, resp);

        verify(resp).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        verify(req).setAttribute(eq("applicants"), anyList());
        verify(req).setAttribute("totalCount", 1);
        verify(req).setAttribute("currentPage", 1);
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/applications.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: filters applications by driveId and status")
    void testFilterByDriveAndStatus() throws ServletException, IOException {
        driveService.createDrive("DRV-2", "CMP-1", "Data Analyst", 7.0, Set.of("CSE"), LocalDate.now().plusDays(5));
        Application app1 = appService.submitApplication("STU-1", "DRV-1");
        Application app2 = appService.submitApplication("STU-1", "DRV-2");

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-token-xyz");
        when(req.getParameter("driveId")).thenReturn("DRV-1");
        when(req.getParameter("status")).thenReturn("APPLIED");

        controller.doGet(req, resp);

        verify(req).setAttribute(eq("applicants"), argThat(list -> {
            List<?> l = (List<?>) list;
            return l.size() == 1;
        }));
    }

    @Test
    @DisplayName("doGet: applies bounded pagination")
    void testPaginationBounds() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-token-xyz");
        when(req.getParameter("page")).thenReturn("2");
        when(req.getParameter("pageSize")).thenReturn("10");

        controller.doGet(req, resp);

        verify(req).setAttribute("pageSize", 10);
    }

    @Test
    @DisplayName("doGet: rejects unauthenticated and student users with 403 Forbidden")
    void testGetRejectsUnauthorized() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentPrincipal);

        controller.doGet(req, resp);

        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Officer account required.");
        verify(dispatcher, never()).forward(req, resp);
    }

    @Test
    @DisplayName("doPost: shortlists eligible candidate via /{id}/shortlist path with valid CSRF")
    void testShortlistSuccessViaPath() throws ServletException, IOException {
        Application app = appService.submitApplication("STU-1", "DRV-1");

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/" + app.getId() + "/shortlist");

        controller.doPost(req, resp);

        Application updated = appRepo.findById(app.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, updated.getStatus());
        verify(session).setAttribute(eq("flash_success"), contains("successfully shortlisted"));
        verify(resp).sendRedirect("/hireflow/officer/applications");
    }

    @Test
    @DisplayName("doPost: shortlists eligible candidate via action=shortlist parameter")
    void testShortlistSuccessViaParam() throws ServletException, IOException {
        Application app = appService.submitApplication("STU-1", "DRV-1");

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getParameter("action")).thenReturn("shortlist");
        when(req.getParameter("applicationId")).thenReturn(app.getId());

        controller.doPost(req, resp);

        Application updated = appRepo.findById(app.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, updated.getStatus());
        verify(session).setAttribute(eq("flash_success"), contains("successfully shortlisted"));
    }

    @Test
    @DisplayName("doPost: rejects missing or invalid CSRF token with 403 Forbidden")
    void testShortlistRejectsInvalidCsrf() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("wrong-csrf");
        when(req.getPathInfo()).thenReturn("/APP-1/shortlist");

        controller.doPost(req, resp);

        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
    }

    @Test
    @DisplayName("doPost: rejects shortlisting ineligible student with flash error")
    void testShortlistIneligibleStudentRejected() throws ServletException, IOException {
        // Manually save an application for Bob (MECH, 6.0 CGPA) into DRV-1 (requires CSE/IT, 7.5 CGPA)
        Application app = new Application("APP-2", "STU-2", "DRV-1", ApplicationStatus.APPLIED);
        appRepo.save(app);

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/APP-2/shortlist");

        controller.doPost(req, resp);

        Application unchanged = appRepo.findById("APP-2").orElseThrow();
        assertEquals(ApplicationStatus.APPLIED, unchanged.getStatus());
        verify(session).setAttribute(eq("flash_error"), contains("academic eligibility"));
        verify(resp).sendRedirect("/hireflow/officer/applications");
    }

    @Test
    @DisplayName("doPost: rejects shortlisting non-existent application")
    void testShortlistNonExistentApplication() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/APP-NONEXISTENT/shortlist");

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("not found"));
        verify(resp).sendRedirect("/hireflow/officer/applications");
    }

    @Test
    @DisplayName("doPost: rejects shortlisting an application in invalid status like REJECTED")
    void testShortlistInvalidStatusTransition() throws ServletException, IOException {
        Application app = new Application("APP-1", "STU-1", "DRV-1", ApplicationStatus.REJECTED);
        appRepo.save(app);

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/APP-1/shortlist");

        controller.doPost(req, resp);

        Application unchanged = appRepo.findById("APP-1").orElseThrow();
        assertEquals(ApplicationStatus.REJECTED, unchanged.getStatus());
        verify(session).setAttribute(eq("flash_error"), contains("Invalid status transition"));
        verify(resp).sendRedirect("/hireflow/officer/applications");
    }

    @Test
    @DisplayName("doPost: is idempotent if application is already SHORTLISTED")
    void testShortlistIdempotency() throws ServletException, IOException {
        Application app = new Application("APP-1", "STU-1", "DRV-1", ApplicationStatus.SHORTLISTED);
        appRepo.save(app);

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/APP-1/shortlist");

        controller.doPost(req, resp);

        Application updated = appRepo.findById("APP-1").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, updated.getStatus());
        verify(session).setAttribute(eq("flash_success"), contains("successfully shortlisted"));
    }

    @Test
    @DisplayName("doPost: rejects non-officer with 403 Forbidden")
    void testPostRejectsUnauthorizedRole() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentPrincipal);

        controller.doPost(req, resp);

        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Officer account required.");
    }
}
