package com.hireflow.web.filter;

import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Deny-by-default authorization filter protecting {@code /student/*} and {@code /officer/*} endpoints.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Zero trust for client-supplied URL parameters (e.g. {@code ?role=ADMIN} or {@code ?studentId=101}).</li>
 *     <li>Requires a cryptographically established server-side {@link HttpSession} containing a valid {@link UserPrincipal}.</li>
 *     <li>Unauthenticated requests receive HTTP 403 Forbidden and are forwarded to a sanitized security gate view.</li>
 *     <li>Sets no-cache headers to prevent intermediate proxies from caching access-denied pages.</li>
 * </ul>
 */
@WebFilter(filterName = "PortalAuthorizationFilter", urlPatterns = {"/student/*", "/officer/*"})
public class PortalAuthorizationFilter implements Filter {

    public static final String SESSION_USER_ATTR = "hireflow.user";
    private static final String AUTH_GATE_VIEW = "/WEB-INF/views/auth-gate.jsp";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Apply strict anti-caching headers on all protected route evaluations
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        String path = req.getRequestURI();
        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null) ? (UserPrincipal) session.getAttribute(SESSION_USER_ATTR) : null;

        // Security check: Never trust client parameters for authentication or identity
        if (principal == null) {
            denyAccess(req, resp, "Authentication Required: You must be logged in to access protected portal features.");
            return;
        }

        // Revalidate account status for immediate session revocation if account is disabled or deleted
        ServletContext sc = req.getServletContext();
        if (sc != null) {
            Object repoAttr = sc.getAttribute(com.hireflow.web.listener.WebContextListener.USER_ACCOUNT_REPO_ATTR);
            if (repoAttr instanceof com.hireflow.web.security.UserAccountRepository repo) {
                java.util.Optional<com.hireflow.web.security.UserAccount> optAccount = repo.findByUsername(principal.getName());
                if (optAccount.isEmpty() || !optAccount.get().isEnabled()) {
                    if (session != null) {
                        session.invalidate();
                    }
                    denyAccess(req, resp, "Access Denied: Your account has been disabled or removed. Please contact the administrator.");
                    return;
                }
            }
        }

        // Role-based path authorization check
        if (path.contains("/student/") && !principal.isStudent()) {
            denyAccess(req, resp, "Access Denied: Candidate student portal requires a STUDENT role account.");
            return;
        }

        if (path.contains("/officer/") && !principal.isPlacementOfficer()) {
            denyAccess(req, resp, "Access Denied: TPC Officer console requires a PLACEMENT_OFFICER role account.");
            return;
        }

        if (path.contains("/admin/") && !principal.isAdmin()) {
            denyAccess(req, resp, "Access Denied: Administrator console requires an ADMINISTRATOR role account.");
            return;
        }

        // Authenticated and authorized
        chain.doFilter(request, response);
    }

    private void denyAccess(HttpServletRequest req, HttpServletResponse resp, String reason)
            throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.setAttribute("authReason", reason);
        req.setAttribute("targetUri", req.getRequestURI());
        req.getRequestDispatcher(AUTH_GATE_VIEW).forward(req, resp);
    }

    @Override
    public void destroy() {
    }
}
