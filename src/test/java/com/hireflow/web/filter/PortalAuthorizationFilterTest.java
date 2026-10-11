package com.hireflow.web.filter;

import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

@DisplayName("PortalAuthorizationFilter - Protected Route Access & Tampering Defense Tests")
class PortalAuthorizationFilterTest {

    private PortalAuthorizationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private RequestDispatcher requestDispatcher;

    @BeforeEach
    void setUp() {
        filter = new PortalAuthorizationFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        requestDispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher("/WEB-INF/views/auth-gate.jsp")).thenReturn(requestDispatcher);
    }

    @Test
    @DisplayName("security: unauthenticated request to /student/* is denied with HTTP 403 and gate view")
    void testUnauthenticatedAccessToStudentRouteDeniedWith403() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/student/profile");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(request).setAttribute(eq("authReason"), anyString());
        verify(requestDispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("security: unauthenticated request to /officer/* is denied with HTTP 403 and gate view")
    void testUnauthenticatedAccessToOfficerRouteDeniedWith403() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/officer/dashboard");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(requestDispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("security: client parameter tampering (?role=STUDENT&studentId=STU-101) is strictly ignored without session")
    void testParameterTamperingCannotBypassAuthentication() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/student/applications");
        when(request.getParameter("role")).thenReturn("STUDENT");
        when(request.getParameter("studentId")).thenReturn("STU-101");
        when(request.getSession(false)).thenReturn(null); // No actual session

        filter.doFilter(request, response, chain);

        // Must still receive 403!
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("security: authenticated STUDENT principal is allowed access to /student/*")
    void testAuthenticatedStudentAccessAllowedForStudentRoute() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/student/profile");
        HttpSession session = mock(HttpSession.class);
        UserPrincipal principal = new UserPrincipal("alice", "Alice Smith", UserRole.STUDENT, "STU-101");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("security: authenticated STUDENT principal is DENIED access to /officer/* (role enforcement)")
    void testStudentAccessToOfficerRouteDenied() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/officer/publish-schedule");
        HttpSession session = mock(HttpSession.class);
        UserPrincipal principal = new UserPrincipal("alice", "Alice Smith", UserRole.STUDENT, "STU-101");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("security: authenticated PLACEMENT_OFFICER principal is allowed access to /officer/*")
    void testAuthenticatedOfficerAllowedForOfficerRoute() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/officer/dashboard");
        HttpSession session = mock(HttpSession.class);
        UserPrincipal principal = new UserPrincipal("admin", "Director TPC", UserRole.PLACEMENT_OFFICER, null);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("security: strict anti-caching headers applied on all protected route evaluations")
    void testNoCacheHeadersApplied() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/hireflow/student/profile");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0);
    }
}
