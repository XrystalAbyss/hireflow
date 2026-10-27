package com.hireflow.web.controller;

import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.InMemoryApplicationRepository;
import com.hireflow.repository.InMemoryCompanyRepository;
import com.hireflow.repository.InMemoryDriveRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.CompanyService;
import com.hireflow.service.RecruitmentDriveService;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerDriveController - Recruitment Drive Management Tests")
class OfficerDriveControllerTest {

    private InMemoryCompanyRepository companyRepo;
    private InMemoryDriveRepository driveRepo;
    private InMemoryApplicationRepository appRepo;

    private CompanyService companyService;
    private RecruitmentDriveService driveService;
    private ApplicationService appService;
    private OfficerDriveController controller;

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
        appRepo = new InMemoryApplicationRepository();

        companyService = new CompanyService(companyRepo);
        driveService = new RecruitmentDriveService(driveRepo, companyRepo, appRepo);
        controller = new OfficerDriveController(driveService, companyService, appService);

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

        companyService.registerCompany("CMP-1", "Google", "Tech");
    }

    @Test
    @DisplayName("doGet: lists recruitment drives for authenticated officer")
    void testDrivesListAllowedForOfficer() throws ServletException, IOException {
        driveService.createDrive("DRV-1", "CMP-1", "SWE", 7.5, Set.of("CSE"), LocalDate.now().plusDays(10));
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");

        controller.doGet(req, resp);

        verify(req).setAttribute(eq("drives"), any());
        verify(req).setAttribute(eq("companyNames"), any());
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/drives.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: renders create form for /new path")
    void testNewDriveFormRendered() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");
        when(req.getPathInfo()).thenReturn("/new");

        controller.doGet(req, resp);

        verify(req).setAttribute("formMode", "create");
        verify(req).setAttribute(eq("companies"), any());
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/drive-form.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: renders edit form for existing drive")
    void testEditDriveFormRendered() throws ServletException, IOException {
        driveService.createDrive("DRV-1", "CMP-1", "SWE", 7.5, Set.of("CSE"), LocalDate.now().plusDays(10));
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");
        when(req.getPathInfo()).thenReturn("/DRV-1/edit");

        controller.doGet(req, resp);

        verify(req).setAttribute("formMode", "edit");
        verify(req).setAttribute(eq("drive"), any(RecruitmentDrive.class));
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/drive-form.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: renders drive detail view")
    void testDriveDetailRendered() throws ServletException, IOException {
        driveService.createDrive("DRV-1", "CMP-1", "SWE", 7.5, Set.of("CSE"), LocalDate.now().plusDays(10));
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");
        when(req.getPathInfo()).thenReturn("/DRV-1");

        controller.doGet(req, resp);

        verify(req).setAttribute(eq("drive"), any(RecruitmentDrive.class));
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/drive-detail.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doPost: creates drive with valid CSRF and redirects with PRG")
    void testCreateDriveSuccessWithPrg() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getParameter("id")).thenReturn("DRV-NEW");
        when(req.getParameter("companyId")).thenReturn("CMP-1");
        when(req.getParameter("jobTitle")).thenReturn("SDE Intern");
        when(req.getParameter("minimumCgpa")).thenReturn("8.00");
        when(req.getParameter("eligibleBranches")).thenReturn("CSE, IT");
        when(req.getParameter("applicationDeadline")).thenReturn(LocalDate.now().plusDays(15).toString());

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_success"), contains("DRV-NEW"));
        verify(resp).sendRedirect("/hireflow/officer/drives");
    }

    @Test
    @DisplayName("doPost: invalid deadline in past redirects to form with flash error")
    void testCreateDrivePastDeadlineRejected() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getParameter("id")).thenReturn("DRV-BAD");
        when(req.getParameter("companyId")).thenReturn("CMP-1");
        when(req.getParameter("jobTitle")).thenReturn("SDE Intern");
        when(req.getParameter("minimumCgpa")).thenReturn("8.00");
        when(req.getParameter("eligibleBranches")).thenReturn("CSE");
        when(req.getParameter("applicationDeadline")).thenReturn(LocalDate.now().minusDays(5).toString());

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("past"));
        verify(resp).sendRedirect("/hireflow/officer/drives/new");
    }

    @Test
    @DisplayName("doPost: closes drive by setting isClosed=true and preserving deadline")
    void testCloseDriveSuccess() throws ServletException, IOException {
        LocalDate originalDeadline = LocalDate.now().plusDays(10);
        driveService.createDrive("DRV-1", "CMP-1", "SWE", 7.5, Set.of("CSE"), originalDeadline);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/DRV-1/close");

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_success"), contains("closed"));
        verify(resp).sendRedirect("/hireflow/officer/drives");

        RecruitmentDrive updated = driveService.getDriveOrThrow("DRV-1");
        assertTrue(updated.isClosed());
        assertEquals(originalDeadline, updated.getApplicationDeadline());
        assertFalse(updated.isOpen(LocalDate.now()));
        assertFalse(updated.isDeadlinePassed(LocalDate.now()));
    }

    @Test
    @DisplayName("doPost: missing or invalid CSRF returns 403 Forbidden")
    void testMissingCsrfRejected() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("wrong-csrf");

        controller.doPost(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));
        verify(resp, never()).sendRedirect(anyString());
    }

    @Test
    @DisplayName("doGet & doPost: student role is rejected with 403 Forbidden")
    void testStudentRoleRejected() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentPrincipal);

        controller.doGet(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());

        controller.doPost(req, resp);
        verify(resp, times(2)).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }
}
