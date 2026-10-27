package com.hireflow.web.controller;

import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Controller guarding protected student and officer subpaths.
 * Provides defense-in-depth enforcement: unauthenticated requests receive HTTP 403 Forbidden.
 */
@WebServlet(name = "ProtectedPortalController", urlPatterns = {"/student/*", "/officer/*"})
public class ProtectedPortalController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String AUTH_GATE_VIEW = "/WEB-INF/views/auth-gate.jsp";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleProtectedRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleProtectedRequest(req, resp);
    }

    private void handleProtectedRequest(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;

        if (principal == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.setAttribute("authReason", "Protected portal feature. Full authentication will be enabled in Stage 3C.");
            req.setAttribute("targetUri", req.getRequestURI());
            req.getRequestDispatcher(AUTH_GATE_VIEW).forward(req, resp);
            return;
        }

        // Placeholder for future Stage 3C authenticated dispatch
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Stage 3C authentication underway.");
    }
}
