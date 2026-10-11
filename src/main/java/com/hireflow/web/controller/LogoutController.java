package com.hireflow.web.controller;

import com.hireflow.web.security.CsrfTokenManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Controller managing secure logout operations.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Accepts state-changing logout requests strictly via POST.</li>
 *     <li>Requires valid CSRF token validation before invalidating sessions.</li>
 *     <li>Completely invalidates the server-side {@link HttpSession}.</li>
 *     <li>Explicitly clears the {@code JSESSIONID} client cookie.</li>
 *     <li>Redirects to the login route with safe notification indicator.</li>
 * </ul>
 */
public class LogoutController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LogoutController.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Enforce POST for state modification to defend against cross-site image-tag logout attacks
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Logout requires HTTP POST with valid CSRF token.");
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
            LOGGER.warning("[Security] Rejected POST /logout with missing or invalid CSRF token from IP: " + req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF security token.");
            return;
        }

        // 2. Invalidate server session
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // 3. Clear JSESSIONID cookie on the client scoped strictly to actual servlet context
        String cookiePath = req.getContextPath().isEmpty() ? "/" : req.getContextPath();
        Cookie killCookie = new Cookie("JSESSIONID", "");
        killCookie.setPath(cookiePath);
        killCookie.setMaxAge(0);
        killCookie.setHttpOnly(true);
        if (req.isSecure()) {
            killCookie.setSecure(true);
        }
        resp.addCookie(killCookie);

        // 4. Redirect to login
        resp.sendRedirect(req.getContextPath() + "/login?loggedOut=true");
    }
}
