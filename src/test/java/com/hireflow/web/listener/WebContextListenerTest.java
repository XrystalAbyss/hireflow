package com.hireflow.web.listener;

import com.hireflow.context.ApplicationContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("WebContextListener - ServletContext Lifecycle and Safety Guard Tests")
class WebContextListenerTest {

    @Test
    @DisplayName("lifecycle: initializes and binds ApplicationContext and services in IN_MEMORY mode")
    void testContextInitializedStoresServicesInServletContext() {
        WebContextListener listener = new WebContextListener();
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext sc = mock(ServletContext.class);

        when(event.getServletContext()).thenReturn(sc);
        when(sc.getInitParameter("hireflow.storage.mode")).thenReturn("IN_MEMORY");

        listener.contextInitialized(event);

        verify(sc).setAttribute(eq(WebContextListener.APP_CONTEXT_ATTR), any(ApplicationContext.class));
        verify(sc).setAttribute(eq(WebContextListener.DRIVE_SERVICE_ATTR), any());
        verify(sc).setAttribute(eq(WebContextListener.COMPANY_REPO_ATTR), any());
    }

    @Test
    @DisplayName("lifecycle: cleanly closes ApplicationContext on contextDestroyed")
    void testContextDestroyedClosesApplicationContext() {
        WebContextListener listener = new WebContextListener();
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext sc = mock(ServletContext.class);
        ApplicationContext appContext = mock(ApplicationContext.class);

        when(event.getServletContext()).thenReturn(sc);
        when(sc.getAttribute(WebContextListener.APP_CONTEXT_ATTR)).thenReturn(appContext);

        listener.contextDestroyed(event);

        verify(appContext).close();
        verify(sc).removeAttribute(WebContextListener.APP_CONTEXT_ATTR);
        verify(sc).removeAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        verify(sc).removeAttribute(WebContextListener.COMPANY_REPO_ATTR);
    }

    @Test
    @DisplayName("safety: failing PostgreSQL connection in JDBC_DEV throws IllegalStateException and NEVER silently falls back to IN_MEMORY")
    void testPostgreSqlStartupFailureDoesNotSilentlyFallback() {
        // Exercise a genuine authentication failure only on the isolated endpoint.
        var failedConfig = com.hireflow.db.DatabaseConfig.builder()
            .mode(com.hireflow.db.DatabaseMode.JDBC_DEV)
            .jdbcUrl(com.hireflow.testsupport.CoreTestDatabase.url())
            .username("hireflow_cap8_app").password("intentionally-invalid-core-test-password")
            .connectionTimeoutMs(1000).build();
        try (var configLoader = mockStatic(com.hireflow.db.DatabaseConfig.class, CALLS_REAL_METHODS)) {
            configLoader.when(com.hireflow.db.DatabaseConfig::load).thenReturn(failedConfig);
            WebContextListener listener = new WebContextListener();
            ServletContextEvent event = mock(ServletContextEvent.class);
            ServletContext sc = mock(ServletContext.class);

            when(event.getServletContext()).thenReturn(sc);
            when(sc.getInitParameter("hireflow.storage.mode")).thenReturn("JDBC_DEV");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    listener.contextInitialized(event),
                    "WebContextListener must fail fast and refuse to silently fall back to IN_MEMORY when JDBC fails"
            );

            assertTrue(ex.getMessage().contains("failed to initialize database context"),
                    "Exception message must clearly report database failure: " + ex.getMessage());
            verify(sc, never()).setAttribute(eq(WebContextListener.APP_CONTEXT_ATTR), any());
        }
    }
}
