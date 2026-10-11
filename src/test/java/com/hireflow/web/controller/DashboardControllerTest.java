package com.hireflow.web.controller;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("DashboardControllers - Student and Officer Dashboard Authorization Tests")
class DashboardControllerTest {

    private StudentDashboardController studentController;
    private OfficerDashboardController officerController;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher requestDispatcher;

    @BeforeEach
    void setUp() {
        studentController = new StudentDashboardController();
        officerController = new OfficerDashboardController();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        requestDispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
    }

    @Test
    @DisplayName("student: student dashboard forwards to view for authenticated STUDENT")
    void testStudentDashboardAllowed() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal("student1", "Student One", UserRole.STUDENT, "STU-101");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-token");

        studentController.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/student/dashboard.jsp");
        verify(requestDispatcher).forward(request, response);
    }

    @Test
    @DisplayName("student: student dashboard rejects officer or unauthenticated user (403)")
    void testStudentDashboardRejectsOfficer() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal("officer1", "Officer One", UserRole.PLACEMENT_OFFICER, null);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        studentController.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(requestDispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("officer: officer dashboard forwards to view for authenticated PLACEMENT_OFFICER")
    void testOfficerDashboardAllowed() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal("officer1", "Officer One", UserRole.PLACEMENT_OFFICER, null);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-token");

        officerController.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/officer/dashboard.jsp");
        verify(requestDispatcher).forward(request, response);
    }

    @Test
    @DisplayName("officer: officer dashboard rejects student user (403)")
    void testOfficerDashboardRejectsStudent() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal("student1", "Student One", UserRole.STUDENT, "STU-101");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        officerController.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(requestDispatcher, never()).forward(request, response);
    }
}
