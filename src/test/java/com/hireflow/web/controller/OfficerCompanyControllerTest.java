package com.hireflow.web.controller;

import com.hireflow.model.Company;
import com.hireflow.repository.InMemoryCompanyRepository;
import com.hireflow.service.CompanyService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerCompanyController - Corporate Partner Management Tests")
class OfficerCompanyControllerTest {

    private InMemoryCompanyRepository companyRepo;
    private CompanyService companyService;
    private OfficerCompanyController controller;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal officerPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        companyService = new CompanyService(companyRepo);
        controller = new OfficerCompanyController(companyService);

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
    }

    @Test
    @DisplayName("doGet: lists companies for authenticated officer")
    void testCompanyListAllowedForOfficer() throws ServletException, IOException {
        companyService.registerCompany("CMP-1", "Google", "Tech");
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");

        controller.doGet(req, resp);

        verify(req).setAttribute(eq("companies"), any());
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/companies.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: renders create form for /new path")
    void testNewCompanyFormRendered() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");
        when(req.getPathInfo()).thenReturn("/new");

        controller.doGet(req, resp);

        verify(req).setAttribute("formMode", "create");
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/company-form.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: renders edit form for existing company")
    void testEditCompanyFormRendered() throws ServletException, IOException {
        companyService.registerCompany("CMP-1", "Google", "Tech");
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-123");
        when(req.getPathInfo()).thenReturn("/CMP-1/edit");

        controller.doGet(req, resp);

        verify(req).setAttribute("formMode", "edit");
        verify(req).setAttribute(eq("company"), any(Company.class));
        verify(req).getRequestDispatcher("/WEB-INF/views/officer/company-form.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doPost: creates company with valid CSRF and redirects with PRG")
    void testCreateCompanySuccessWithPrg() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getParameter("id")).thenReturn("CMP-NEW");
        when(req.getParameter("name")).thenReturn("Microsoft");
        when(req.getParameter("industry")).thenReturn("Cloud");

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_success"), contains("Microsoft"));
        verify(resp).sendRedirect("/hireflow/officer/companies");
    }

    @Test
    @DisplayName("doPost: rejects duplicate company and redirects to form with flash error")
    void testCreateCompanyDuplicateRejected() throws ServletException, IOException {
        companyService.registerCompany("CMP-DUP", "Existing Corp", "Finance");
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getParameter("id")).thenReturn("CMP-DUP");
        when(req.getParameter("name")).thenReturn("New Name");
        when(req.getParameter("industry")).thenReturn("Finance");

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("already exists"));
        verify(resp).sendRedirect("/hireflow/officer/companies/new");
    }

    @Test
    @DisplayName("doPost: updates company fields successfully with PRG")
    void testUpdateCompanySuccessWithPrg() throws ServletException, IOException {
        companyService.registerCompany("CMP-1", "Google", "Tech");
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-csrf");
        when(req.getParameter("_csrf")).thenReturn("valid-csrf");
        when(req.getPathInfo()).thenReturn("/CMP-1/update");
        when(req.getParameter("name")).thenReturn("Alphabet Inc.");
        when(req.getParameter("industry")).thenReturn("Search & AI");

        controller.doPost(req, resp);

        verify(session).setAttribute(eq("flash_success"), contains("Alphabet Inc."));
        verify(resp).sendRedirect("/hireflow/officer/companies");
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
