package com.hireflow.web.controller;

import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.AuthenticationResult;
import com.hireflow.web.security.AuthenticationService;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Controller handling user authentication lifecycle (GET /login and POST /login).
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Enforces server-side CSRF validation on credentials submission.</li>
 *     <li>Rotates session ID upon successful authentication (defends against Session Fixation).</li>
 *     <li>Disallows external or arbitrary unvalidated open redirects.</li>
 *     <li>Generic, constant feedback for invalid authentication credentials.</li>
 *     <li>Zero storage of plaintext passwords in HTTP sessions or logs.</li>
 * </ul>
 */
public class LoginController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final String LOGIN_VIEW = "/WEB-INF/views/login.jsp";

    private AuthenticationService authService;

    @Override
    public void init() {
        Object attr = getServletContext().getAttribute(WebContextListener.AUTH_SERVICE_ATTR);
        if (attr instanceof AuthenticationService) {
            this.authService = (AuthenticationService) attr;
        }
    }

    // Constructor for testing with dependency injection
    public LoginController(AuthenticationService authService) {
        this.authService = authService;
    }

    public LoginController() {
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Anti-caching headers on login page
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HttpSession existingSession = req.getSession(false);
        if (existingSession != null) {
            UserPrincipal principal = (UserPrincipal) existingSession.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR);
            if (principal != null) {
                // User already authenticated; redirect to their respective dashboard
                redirectToDashboard(req, resp, principal);
                return;
            }
        }

        // Establish session to host CSRF token
        HttpSession session = req.getSession(true);
        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("csrfToken", csrfToken);

        // Check for informational query params (e.g. ?loggedOut=true or ?sessionExpired=true)
        if ("true".equalsIgnoreCase(req.getParameter("loggedOut"))) {
            req.setAttribute("flashType", "info");
            req.setAttribute("flashMessage", "You have been logged out successfully.");
        } else if ("true".equalsIgnoreCase(req.getParameter("sessionExpired"))) {
            req.setAttribute("flashType", "warn");
            req.setAttribute("flashMessage", "Your session has expired. Please sign in again.");
        }

        String returnUrl = sanitizeReturnUrl(req.getParameter("returnUrl"), req.getContextPath());
        if (returnUrl != null) {
            req.setAttribute("returnUrl", returnUrl);
        }

        req.getRequestDispatcher(LOGIN_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Anti-caching
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        // 1. Enforce CSRF token verification
        if (!CsrfTokenManager.validateToken(req)) {
            LOGGER.warning("[Security] Rejected POST /login with missing or invalid CSRF token from IP: " + req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF security token.");
            return;
        }

        String username = req.getParameter("username");
        String passwordStr = req.getParameter("password");
        String returnUrl = sanitizeReturnUrl(req.getParameter("returnUrl"), req.getContextPath());

        char[] passwordChars = (passwordStr != null) ? passwordStr.toCharArray() : new char[0];

        try {
            AuthenticationService service = resolveAuthService();
            AuthenticationResult result = service.authenticate(username, passwordChars, req.getRemoteAddr());

            if (result.isSuccess() && result.getPrincipal().isPresent()) {
                UserPrincipal principal = result.getPrincipal().get();

                // 2. Rotate session ID to eliminate Session Fixation vulnerabilities
                req.changeSessionId();
                HttpSession session = req.getSession(true);
                session.setAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR, principal);
                // Rotate CSRF token for the new authenticated session state
                CsrfTokenManager.rotateToken(session);

                LOGGER.info("[Auth] User authenticated successfully: " + principal.getName() + " (" + principal.getRole() + ")");

                // 3. Role-based redirect or safe return URL
                if (returnUrl != null && isAuthorizedForReturnUrl(principal, returnUrl, req.getContextPath())) {
                    resp.sendRedirect(returnUrl);
                } else {
                    redirectToDashboard(req, resp, principal);
                }
            } else {
                LOGGER.warning("[Auth] Failed authentication attempt for user input");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                req.setAttribute("flashType", "error");
                req.setAttribute("flashMessage", result.getErrorMessage());
                req.setAttribute("submittedUsername", username != null ? username.trim() : "");
                if (returnUrl != null) {
                    req.setAttribute("returnUrl", returnUrl);
                }
                HttpSession session = req.getSession(true);
                req.setAttribute("csrfToken", CsrfTokenManager.getOrCreateToken(session));
                req.getRequestDispatcher(LOGIN_VIEW).forward(req, resp);
            }
        } finally {
            // Secure memory wipe of plaintext password characters
            Arrays.fill(passwordChars, '\0');
        }
    }

    private void redirectToDashboard(HttpServletRequest req, HttpServletResponse resp, UserPrincipal principal)
            throws IOException {
        String contextPath = req.getContextPath();
        if (principal.isStudent()) {
            resp.sendRedirect(contextPath + "/student/dashboard");
        } else {
            resp.sendRedirect(contextPath + "/officer/dashboard");
        }
    }

    private String sanitizeReturnUrl(String candidate, String contextPath) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return null;
        }
        String trimmed = candidate.trim();
        // Prevent open redirect attacks: candidate must be relative and start with /
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            return null;
        }
        // Disallow looping back to login or logout
        if (trimmed.contains("/login") || trimmed.contains("/logout")) {
            return null;
        }
        return trimmed;
    }

    private boolean isAuthorizedForReturnUrl(UserPrincipal principal, String returnUrl, String contextPath) {
        String relative = returnUrl.startsWith(contextPath) ? returnUrl.substring(contextPath.length()) : returnUrl;
        if (relative.startsWith("/student/") && !principal.isStudent()) {
            return false;
        }
        if (relative.startsWith("/officer/") && !principal.isPlacementOfficer()) {
            return false;
        }
        return true;
    }

    private AuthenticationService resolveAuthService() {
        if (this.authService != null) {
            return this.authService;
        }
        Object attr = getServletContext().getAttribute(WebContextListener.AUTH_SERVICE_ATTR);
        if (attr instanceof AuthenticationService) {
            this.authService = (AuthenticationService) attr;
            return this.authService;
        }
        throw new IllegalStateException("AuthenticationService is not available in ServletContext");
    }
}
