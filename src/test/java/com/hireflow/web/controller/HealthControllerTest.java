package com.hireflow.web.controller;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseMode;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.web.listener.WebContextListener;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HealthController - System Health Check API Tests")
class HealthControllerTest {

    private HealthController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ServletContext servletContext;
    private ServletConfig servletConfig;
    private ApplicationContext appContext;
    private RecruitmentDriveService driveService;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        controller = new HealthController();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        servletContext = mock(ServletContext.class);
        servletConfig = mock(ServletConfig.class);
        appContext = mock(ApplicationContext.class);
        driveService = mock(RecruitmentDriveService.class);
        responseWriter = new StringWriter();

        when(servletConfig.getServletContext()).thenReturn(servletContext);
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        controller.init(servletConfig);
    }

    @Test
    @DisplayName("doGet: returns HTTP 200, status UP, and sanitized JSON for healthy context")
    void testHealthReturnsValidJsonAndHttp200() throws IOException {
        when(servletContext.getAttribute(WebContextListener.APP_CONTEXT_ATTR)).thenReturn(appContext);
        when(appContext.isClosed()).thenReturn(false);
        when(appContext.getMode()).thenReturn(DatabaseMode.IN_MEMORY);
        when(appContext.getDriveService()).thenReturn(driveService);
        when(driveService.getTotalDrivesCount()).thenReturn(3);

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentType("application/json;charset=UTF-8");

        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\": \"UP\""), "Should contain UP status");
        assertTrue(body.contains("\"service\": \"HireFlow Web Portal\""), "Should contain service name");
        assertTrue(body.contains("\"storageMode\": \"IN_MEMORY\""), "Should contain storage mode");
        assertTrue(body.contains("\"totalDrives\": 3"), "Should contain drive count");
    }

    @Test
    @DisplayName("security: health JSON never leaks credentials, passwords, or host connection strings")
    void testHealthDoesNotExposeSecretsOrConnectionStrings() throws IOException {
        when(servletContext.getAttribute(WebContextListener.APP_CONTEXT_ATTR)).thenReturn(appContext);
        when(appContext.isClosed()).thenReturn(false);
        when(appContext.getMode()).thenReturn(DatabaseMode.JDBC_DEV);
        when(appContext.getDataSourceProvider()).thenReturn(Optional.empty());
        when(appContext.getDriveService()).thenReturn(driveService);

        controller.doGet(request, response);

        String body = responseWriter.toString().toLowerCase();
        assertFalse(body.contains("password"), "Must not leak password");
        assertFalse(body.contains("secret"), "Must not leak secret");
        assertFalse(body.contains("jdbc:postgresql"), "Must not leak JDBC URL");
        assertFalse(body.contains("localhost:5432"), "Must not leak host/port");
        assertFalse(body.contains("hireflow_user"), "Must not leak DB user");
    }

    @Test
    @DisplayName("doGet: returns HTTP 503 and DOWN status when ApplicationContext is closed or unavailable")
    void testHealthReflectsClosedContext() throws IOException {
        when(servletContext.getAttribute(WebContextListener.APP_CONTEXT_ATTR)).thenReturn(appContext);
        when(appContext.isClosed()).thenReturn(true);
        when(appContext.getMode()).thenReturn(DatabaseMode.IN_MEMORY);

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        String body = responseWriter.toString();
        assertTrue(body.contains("\"status\": \"DOWN\""));
    }
}
