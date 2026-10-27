package com.hireflow.web.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Controller handling public landing page requests (GET / and GET /home).
 * Forwards requests to the {@code /WEB-INF/views/home.jsp} MVC view template.
 */
@WebServlet(name = "HomeController", urlPatterns = {"/home", "/"})
public class HomeController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String VIEW_HOME = "/WEB-INF/views/home.jsp";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();

        // If mapped as default servlet (/), delegate static assets to container default servlet
        if (servletPath != null && servletPath.startsWith("/static/")) {
            jakarta.servlet.RequestDispatcher defaultDispatcher = req.getServletContext().getNamedDispatcher("default");
            if (defaultDispatcher != null) {
                defaultDispatcher.forward(req, resp);
                return;
            }
        }

        // If mapped as default servlet (/), only handle root / or /home; return 404 for any other path
        if (servletPath != null && !"/".equals(servletPath) && !"/home".equals(servletPath) && !servletPath.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (pathInfo != null && !pathInfo.isEmpty() && !"/".equals(pathInfo)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        req.getRequestDispatcher(VIEW_HOME).forward(req, resp);
    }
}
