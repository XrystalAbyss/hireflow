package com.hireflow.web.listener;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web application context lifecycle listener for HireFlow (Jakarta EE 10 / Servlets 6.0).
 * <p>
 * Manages the initialization and clean destruction of the shared {@link ApplicationContext},
 * database connection pool, Flyway migration checks, and domain services.
 * <p>
 * Constraints strictly enforced:
 * <ul>
 *     <li>Zero Swing UI instantiation inside Tomcat.</li>
 *     <li>Fast failure on PostgreSQL connection errors (never silently falls back to in-memory).</li>
 *     <li>Reuses persistent dev database without reseeding or deleting data.</li>
 *     <li>Clean and idempotent HikariCP pool disposal on web shutdown.</li>
 *     <li>TCP server socket listening remains disabled in web runtime.</li>
 * </ul>
 */
@WebListener
public class WebContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(WebContextListener.class.getName());

    public static final String APP_CONTEXT_ATTR = "hireflow.appContext";
    public static final String COMPANY_SERVICE_ATTR = "hireflow.companyService";
    public static final String DRIVE_SERVICE_ATTR = "hireflow.driveService";
    public static final String COMPANY_REPO_ATTR = "hireflow.companyRepository";
    public static final String AUTH_SERVICE_ATTR = "hireflow.authService";
    public static final String USER_ACCOUNT_REPO_ATTR = "hireflow.userAccountRepo";
    public static final String STUDENT_SERVICE_ATTR = "hireflow.studentService";
    public static final String ELIGIBILITY_SERVICE_ATTR = "hireflow.eligibilityService";
    public static final String APPLICATION_SERVICE_ATTR = "hireflow.applicationService";
    public static final String STUDENT_REPO_ATTR = "hireflow.studentRepository";
    public static final String DRIVE_REPO_ATTR = "hireflow.driveRepository";
    public static final String APPLICATION_REPO_ATTR = "hireflow.applicationRepository";
    public static final String INTERVIEWER_REPO_ATTR = "hireflow.interviewerRepository";
    public static final String ROOM_REPO_ATTR = "hireflow.roomRepository";
    public static final String SESSION_REPO_ATTR = "hireflow.sessionRepository";
    public static final String INTERVIEW_SERVICE_ATTR = "hireflow.interviewService";
    public static final String SCHEDULING_ENGINE_ATTR = "hireflow.schedulingEngine";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        LOGGER.info("[HireFlow Web] Initializing ServletContext and shared application services...");

        // 1. Resolve configuration with optional ServletContext init-param overrides
        DatabaseConfig loaded = DatabaseConfig.load();
        String paramMode = sc.getInitParameter("hireflow.storage.mode");
        DatabaseMode mode = paramMode != null && !paramMode.trim().isEmpty()
                ? DatabaseMode.fromString(paramMode.trim())
                : loaded.getMode();

        DatabaseConfig config;
        if (mode.isInMemory()) {
            config = DatabaseConfig.builder()
                    .mode(DatabaseMode.IN_MEMORY)
                    .build();
            LOGGER.info("[HireFlow Web] Storage mode configured as IN_MEMORY.");
        } else {
            config = DatabaseConfig.builder()
                    .mode(mode)
                    .jdbcUrl(loaded.getJdbcUrl())
                    .username(loaded.getUsername())
                    .password(loaded.getPassword())
                    .maximumPoolSize(loaded.getMaximumPoolSize())
                    .minimumIdle(loaded.getMinimumIdle())
                    .connectionTimeoutMs(loaded.getConnectionTimeoutMs())
                    .idleTimeoutMs(loaded.getIdleTimeoutMs())
                    .maxLifetimeMs(loaded.getMaxLifetimeMs())
                    .leakDetectionThresholdMs(loaded.getLeakDetectionThresholdMs())
                    .baselineOnMigrate(loaded.isBaselineOnMigrate())
                    .build();
            LOGGER.info("[HireFlow Web] Storage mode configured as " + mode.name() + " (" + config.getJdbcUrl() + ").");
        }

        // 2. Initialize ApplicationContext safely
        ApplicationContext appContext;
        try {
            appContext = ApplicationContext.create(config);
            LOGGER.info("[HireFlow Web] ApplicationContext initialized successfully: " + appContext.getSafeDiagnosticInfo());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "[HireFlow Web] FATAL: Database initialization failed during web startup.", ex);
            // Strict requirement: Never silently fall back to in-memory mode when JDBC connection fails!
            throw new IllegalStateException("HireFlow Web failed to initialize database context for mode " + mode.name() +
                    ": " + ex.getMessage(), ex);
        }

        // 3. Store shared thread-safe dependencies in ServletContext
        sc.setAttribute(APP_CONTEXT_ATTR, appContext);
        sc.setAttribute(COMPANY_SERVICE_ATTR, appContext.getCompanyService());
        sc.setAttribute(DRIVE_SERVICE_ATTR, appContext.getDriveService());
        sc.setAttribute(COMPANY_REPO_ATTR, appContext.getCompanyRepository());
        sc.setAttribute(AUTH_SERVICE_ATTR, appContext.getAuthenticationService());
        sc.setAttribute(USER_ACCOUNT_REPO_ATTR, appContext.getUserAccountRepository());
        sc.setAttribute(STUDENT_SERVICE_ATTR, appContext.getStudentService());
        sc.setAttribute(ELIGIBILITY_SERVICE_ATTR, appContext.getEligibilityService());
        sc.setAttribute(APPLICATION_SERVICE_ATTR, appContext.getApplicationService());
        sc.setAttribute(STUDENT_REPO_ATTR, appContext.getStudentRepository());
        sc.setAttribute(DRIVE_REPO_ATTR, appContext.getDriveRepository());
        sc.setAttribute(APPLICATION_REPO_ATTR, appContext.getApplicationRepository());
        sc.setAttribute(INTERVIEWER_REPO_ATTR, appContext.getInterviewerRepository());
        sc.setAttribute(ROOM_REPO_ATTR, appContext.getRoomRepository());
        sc.setAttribute(SESSION_REPO_ATTR, appContext.getSessionRepository());
        sc.setAttribute(INTERVIEW_SERVICE_ATTR, appContext.getInterviewService());
        sc.setAttribute(SCHEDULING_ENGINE_ATTR, appContext.getSchedulingEngine());
        LOGGER.info("[HireFlow Web] Shared services and repositories bound to ServletContext.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        LOGGER.info("[HireFlow Web] Destroying ServletContext and releasing shared resources...");
        Object obj = sc.getAttribute(APP_CONTEXT_ATTR);
        if (obj instanceof ApplicationContext) {
            ApplicationContext appContext = (ApplicationContext) obj;
            try {
                appContext.close();
                LOGGER.info("[HireFlow Web] ApplicationContext and connection pool closed cleanly.");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "[HireFlow Web] Error closing ApplicationContext on shutdown", ex);
            }
        }
        sc.removeAttribute(APP_CONTEXT_ATTR);
        sc.removeAttribute(COMPANY_SERVICE_ATTR);
        sc.removeAttribute(DRIVE_SERVICE_ATTR);
        sc.removeAttribute(COMPANY_REPO_ATTR);
        sc.removeAttribute(AUTH_SERVICE_ATTR);
        sc.removeAttribute(USER_ACCOUNT_REPO_ATTR);
        sc.removeAttribute(STUDENT_SERVICE_ATTR);
        sc.removeAttribute(ELIGIBILITY_SERVICE_ATTR);
        sc.removeAttribute(APPLICATION_SERVICE_ATTR);
        sc.removeAttribute(STUDENT_REPO_ATTR);
        sc.removeAttribute(DRIVE_REPO_ATTR);
        sc.removeAttribute(APPLICATION_REPO_ATTR);
        sc.removeAttribute(INTERVIEWER_REPO_ATTR);
        sc.removeAttribute(ROOM_REPO_ATTR);
        sc.removeAttribute(SESSION_REPO_ATTR);
        sc.removeAttribute(INTERVIEW_SERVICE_ATTR);
        sc.removeAttribute(SCHEDULING_ENGINE_ATTR);
    }
}
