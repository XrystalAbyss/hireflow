package com.hireflow.web.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Controller serving the public informational policy and about page (GET /about).
 */
@WebServlet(name = "AboutController", urlPatterns = {"/about"})
public class AboutController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String VIEW_ABOUT = "/WEB-INF/views/about.jsp";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher(VIEW_ABOUT).forward(req, resp);
    }
}
