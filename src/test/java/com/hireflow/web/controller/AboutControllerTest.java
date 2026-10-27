package com.hireflow.web.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

@DisplayName("AboutController - Informational Policies Route Tests")
class AboutControllerTest {

    @Test
    @DisplayName("doGet: forwards /about request to /WEB-INF/views/about.jsp")
    void testAboutRouteForwardsToAboutJsp() throws ServletException, IOException {
        AboutController controller = new AboutController();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(req.getRequestDispatcher("/WEB-INF/views/about.jsp")).thenReturn(dispatcher);

        controller.doGet(req, resp);

        verify(req).getRequestDispatcher("/WEB-INF/views/about.jsp");
        verify(dispatcher).forward(req, resp);
    }
}
