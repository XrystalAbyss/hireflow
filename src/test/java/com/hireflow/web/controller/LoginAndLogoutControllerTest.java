package com.hireflow.web.controller;

import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginAndLogoutController - Authentication Workflow & CSRF Defense Tests")
class LoginAndLogoutControllerTest {

    private AuthenticationService authService;
    private LoginController loginController;
    private LogoutController logoutController;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher requestDispatcher;

    private static final String CSRF_TOKEN = "test-csrf-token-1234567890abcdef1234567890abcdef1234567890abcdef12";

    @BeforeEach
    void setUp() {
        authService = mock(AuthenticationService.class);
        loginController = new LoginController(authService);
        logoutController = new LogoutController();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        requestDispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
        when(request.getContextPath()).thenReturn("/hireflow");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("GET /login: renders login view and establishes CSRF token")
    void testGetLoginRendersViewWithCsrf() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(null);
        when(request.getSession(true)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);

        loginController.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/login.jsp");
        verify(requestDispatcher).forward(request, response);
        verify(request).setAttribute(eq("csrfToken"), eq(CSRF_TOKEN));
    }

    @Test
    @DisplayName("GET /login: redirects already-authenticated student to student dashboard")
    void testGetLoginRedirectsAuthenticatedUser() throws ServletException, IOException {
        UserPrincipal principal = new UserPrincipal("student1", "Student One", UserRole.STUDENT, "STU-101");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);

        loginController.doGet(request, response);

        verify(response).sendRedirect("/hireflow/student/dashboard");
        verify(requestDispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("POST /login: rejects request with missing or invalid CSRF token (HTTP 403)")
    void testPostLoginRejectsMissingCsrf() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);
        when(request.getParameter("csrfToken")).thenReturn(null); // Missing
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);

        loginController.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(authService, never()).authenticate(any(), any(), any());
    }

    @Test
    @DisplayName("POST /login: rotates session ID and establishes authenticated session on success")
    void testPostLoginSuccessRotatesSession() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);
        when(request.getParameter("csrfToken")).thenReturn(CSRF_TOKEN);
        when(request.getParameter("username")).thenReturn("rahul.sharma");
        when(request.getParameter("password")).thenReturn("CorrectPassword123!");
        when(request.getParameter("returnUrl")).thenReturn(null);

        UserPrincipal principal = new UserPrincipal("rahul.sharma", "Rahul Sharma", UserRole.STUDENT, "STU-101");
        when(authService.authenticate(eq("rahul.sharma"), any(char[].class), eq("127.0.0.1")))
                .thenReturn(AuthenticationResult.success(principal));

        when(request.getSession(true)).thenReturn(session);

        loginController.doPost(request, response);

        // Security check: Session ID rotation executed
        verify(request).changeSessionId();
        verify(session).setAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR, principal);
        verify(response).sendRedirect("/hireflow/student/dashboard");
    }

    @Test
    @DisplayName("POST /login: rejects invalid credentials with 401 and generic message")
    void testPostLoginFailureShowsGenericError() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);
        when(request.getParameter("csrfToken")).thenReturn(CSRF_TOKEN);
        when(request.getParameter("username")).thenReturn("target.user");
        when(request.getParameter("password")).thenReturn("WrongPassword");

        when(authService.authenticate(eq("target.user"), any(char[].class), eq("127.0.0.1")))
                .thenReturn(AuthenticationResult.failure(AuthenticationResult.Status.INVALID_CREDENTIALS, "Invalid username or password."));

        when(request.getSession(true)).thenReturn(session);

        loginController.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(request).setAttribute("flashType", "error");
        verify(request).setAttribute("flashMessage", "Invalid username or password.");
        verify(requestDispatcher).forward(request, response);
    }

    @Test
    @DisplayName("POST /login: prevents open redirect attack on returnUrl")
    void testPostLoginPreventsOpenRedirect() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);
        when(request.getParameter("csrfToken")).thenReturn(CSRF_TOKEN);
        when(request.getParameter("username")).thenReturn("admin");
        when(request.getParameter("password")).thenReturn("AdminPass123");
        // Open redirect attempt
        when(request.getParameter("returnUrl")).thenReturn("//evil.com/phish");

        UserPrincipal principal = new UserPrincipal("admin", "TPC Director", UserRole.PLACEMENT_OFFICER, null);
        when(authService.authenticate(eq("admin"), any(char[].class), eq("127.0.0.1")))
                .thenReturn(AuthenticationResult.success(principal));
        when(request.getSession(true)).thenReturn(session);

        loginController.doPost(request, response);

        // Safe redirect to officer dashboard, never to evil.com
        verify(response).sendRedirect("/hireflow/officer/dashboard");
    }

    @Test
    @DisplayName("GET /logout: rejects GET request with HTTP 405 Method Not Allowed")
    void testGetLogoutRejected() throws ServletException, IOException {
        logoutController.doGet(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
    }

    @Test
    @DisplayName("POST /logout: invalidates session and clears JSESSIONID cookie")
    void testPostLogoutInvalidatesSessionAndClearsCookie() throws ServletException, IOException {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn(CSRF_TOKEN);
        when(request.getParameter("csrfToken")).thenReturn(CSRF_TOKEN);

        logoutController.doPost(request, response);

        // Verify session invalidated
        verify(session).invalidate();

        // Verify cookie clearing
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie cookie = cookieCaptor.getValue();
        assertEquals("JSESSIONID", cookie.getName());
        assertEquals(0, cookie.getMaxAge());

        // Verify redirect to login
        verify(response).sendRedirect("/hireflow/login?loggedOut=true");
    }
}
