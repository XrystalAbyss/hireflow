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
        // Set an unreachable DB URL to force connection failure
        System.setProperty("hireflow.db.url", "jdbc:postgresql://127.0.0.1:59999/nonexistent_hireflow_db");
        System.setProperty("hireflow.db.pool.connection-timeout-ms", "1000");

        try {
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
        } finally {
            System.clearProperty("hireflow.db.url");
            System.clearProperty("hireflow.db.pool.connection-timeout-ms");
        }
    }
}
