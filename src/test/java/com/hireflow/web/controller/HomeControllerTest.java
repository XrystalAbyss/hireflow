package com.hireflow.web.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

@DisplayName("HomeController - Public Landing Page Route Tests")
class HomeControllerTest {

    @Test
    @DisplayName("doGet: forwards root route / to /WEB-INF/views/home.jsp")
    void testHomeRootRouteForwardsToHomeJsp() throws ServletException, IOException {
        HomeController controller = new HomeController();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(req.getServletPath()).thenReturn("/");
        when(req.getPathInfo()).thenReturn(null);
        when(req.getRequestDispatcher("/WEB-INF/views/home.jsp")).thenReturn(dispatcher);

        controller.doGet(req, resp);

        verify(req).getRequestDispatcher("/WEB-INF/views/home.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: forwards /home to /WEB-INF/views/home.jsp")
    void testHomeExplicitRouteForwardsToHomeJsp() throws ServletException, IOException {
        HomeController controller = new HomeController();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(req.getServletPath()).thenReturn("/home");
        when(req.getPathInfo()).thenReturn(null);
        when(req.getRequestDispatcher("/WEB-INF/views/home.jsp")).thenReturn(dispatcher);

        controller.doGet(req, resp);

        verify(req).getRequestDispatcher("/WEB-INF/views/home.jsp");
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: sends 404 for invalid subpaths under /home")
    void testHomeInvalidSubpathReturns404() throws ServletException, IOException {
        HomeController controller = new HomeController();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getServletPath()).thenReturn("/home");
        when(req.getPathInfo()).thenReturn("/extra/path");

        controller.doGet(req, resp);

        verify(resp).sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
