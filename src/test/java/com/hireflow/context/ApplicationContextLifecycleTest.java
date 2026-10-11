package com.hireflow.context;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.exception.DatabaseException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.scheduler.SchedulingRequest;
import com.hireflow.scheduler.SchedulingResult;
import com.hireflow.scheduler.TimeWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Stage 2B.6: ApplicationContext Lifecycle & Mode Isolation Tests")
public class ApplicationContextLifecycleTest {

    private static DatabaseConfig testDbConfig;
    private static boolean postgresAvailable = false;

    @BeforeAll
    static void setUpAll() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testDbConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(3)
                .minimumIdle(1)
                .connectionTimeoutMs(3000)
                .build();

        try (com.hireflow.db.DataSourceProvider dsp = new com.hireflow.db.DataSourceProvider(testDbConfig)) {
            postgresAvailable = dsp.verifyConnectionHealth(3);
        } catch (Exception ex) {
            postgresAvailable = false;
        }
    }

    @Test
    @DisplayName("Requirement 1: IN_MEMORY mode launches without DB connection and supports complete Stage 1 operations")
    void testInMemoryModeFullLifecycle() {
        try (ApplicationContext ctx = ApplicationContext.createInMemory()) {
            assertEquals(DatabaseMode.IN_MEMORY, ctx.getMode());
            assertTrue(ctx.getDataSourceProvider().isEmpty());
            assertTrue(ctx.getTransactionManager().isEmpty());
            assertTrue(ctx.getFlywayMigrationService().isEmpty());

            // All seven repositories are Stage 1 in-memory stores
            assertInstanceOf(InMemoryStudentRepository.class, ctx.getStudentRepository());
            assertInstanceOf(InMemoryCompanyRepository.class, ctx.getCompanyRepository());
            assertInstanceOf(InMemoryDriveRepository.class, ctx.getDriveRepository());
            assertInstanceOf(InMemoryApplicationRepository.class, ctx.getApplicationRepository());
            assertInstanceOf(InMemoryInterviewerRepository.class, ctx.getInterviewerRepository());
            assertInstanceOf(InMemoryRoomRepository.class, ctx.getRoomRepository());
            assertInstanceOf(InMemoryInterviewSessionRepository.class, ctx.getSessionRepository());

            // Full Stage 1 workflow execution
            Student s = ctx.getStudentService().registerStudent(
                    "STU-MEM-01", "Memory Student", "mem@univ.edu", "CSE", 9.1, 2026, true
            );
            Company c = ctx.getCompanyRepository().add(new Company("CMP-MEM-01", "Memory Corp", "Software"));
            RecruitmentDrive d = ctx.getDriveService().createDrive(
                    "DRV-MEM-01", c.getId(), "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            Application app = ctx.getApplicationService().submitApplication(s.getId(), d.getId());
            assertEquals(ApplicationStatus.APPLIED, app.getStatus());

            ctx.getApplicationService().shortlistApplication(app.getId());
            assertEquals(ApplicationStatus.SHORTLISTED, ctx.getApplicationService().getApplicationOrThrow(app.getId()).getStatus());

            Interviewer inv = ctx.getInterviewerRepository().add(new Interviewer("INT-MEM-01", "Interviewer One", c.getId()));
            Room rm = ctx.getRoomRepository().add(new Room("RM-MEM-01", "Room Alpha", 2));

            // Greedy scheduler execution
            LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
            TimeWindow window = new TimeWindow(start, start.plusHours(2));
            SchedulingRequest req = SchedulingRequest.builder()
                    .applications(List.of(app))
                    .interviewers(List.of(inv))
                    .rooms(List.of(rm))
                    .schedulingWindow(window)
                    .interviewDuration(Duration.ofMinutes(45))
                    .bufferDuration(Duration.ofMinutes(15))
                    .addDriveCompany(d.getId(), c.getId())
                    .build();

            SchedulingResult result = ctx.getSchedulingEngine().schedule(req);
            assertEquals(1, result.getTotalScheduled());

            // In-memory publication
            var pubResult = ctx.getInterviewService().publishSchedule(result.getScheduledSessions(), Duration.ofMinutes(15));
            assertEquals(1, pubResult.publishedCount());
            assertEquals(ApplicationStatus.SCHEDULED, ctx.getApplicationService().getApplicationOrThrow(app.getId()).getStatus());
        }
    }

    @Test
    @DisplayName("Requirement 3: Normal desktop launch strictly rejects JDBC_TEST mode without explicit test flag")
    void testJdbcTestSafeguardRejectsNormalLaunch() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ApplicationContext.create(testDbConfig, false)
        );
        assertTrue(ex.getMessage().contains("JDBC_TEST mode is reserved for automated integration testing"));
    }

    @Test
    @DisplayName("Requirement 4: PostgreSQL unavailable produces sanitized DatabaseException and permits in-memory fallback")
    void testUnreachableDatabaseErrorAndFallback() {
        DatabaseConfig badConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_DEV)
                .jdbcUrl("jdbc:postgresql://localhost:54399/hireflow_dev")
                .username("hireflow_user")
                .password("super_secret_password_123")
                .connectionTimeoutMs(1000)
                .build();

        DatabaseException ex = assertThrows(DatabaseException.class, () ->
                ApplicationContext.create(badConfig, false)
        );

        // Verify password is NOT leaked in exception message
        assertFalse(ex.getMessage().contains("super_secret_password_123"));

        // Caller can fall back safely to in-memory mode
        try (ApplicationContext fallbackCtx = ApplicationContext.createInMemory()) {
            assertEquals(DatabaseMode.IN_MEMORY, fallbackCtx.getMode());
            assertEquals(0, fallbackCtx.getStudentRepository().count());
        }
    }

    @Test
    @DisplayName("Requirement 14: Guaranteed zero mixed repository modes (all in-memory or all JDBC)")
    void testZeroMixedRepositoryModes() {
        // In-memory verification
        try (ApplicationContext memCtx = ApplicationContext.createInMemory()) {
            assertFalse(memCtx.getStudentRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getCompanyRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getDriveRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getApplicationRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getInterviewerRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getRoomRepository() instanceof AbstractJdbcRepository);
            assertFalse(memCtx.getSessionRepository() instanceof AbstractJdbcRepository);
        }

        // JDBC verification
        assumeTrue(postgresAvailable, "PostgreSQL not running on hireflow_test; skipping JDBC check");
        try (ApplicationContext jdbcCtx = ApplicationContext.createJdbcTest(testDbConfig)) {
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getStudentRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getCompanyRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getDriveRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getApplicationRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getInterviewerRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getRoomRepository());
            assertInstanceOf(AbstractJdbcRepository.class, jdbcCtx.getSessionRepository());
        }
    }

    @Test
    @DisplayName("Requirement 18: Shutdown closes database connection pool cleanly and idempotently")
    void testCleanShutdownClosesConnectionPool() {
        assumeTrue(postgresAvailable, "PostgreSQL not running on hireflow_test; skipping pool shutdown check");

        ApplicationContext ctx = ApplicationContext.createJdbcTest(testDbConfig);
        assertTrue(ctx.getDataSourceProvider().isPresent());
        assertFalse(ctx.getDataSourceProvider().get().isClosed());
        assertFalse(ctx.isClosed());

        // Close context
        ctx.close();
        assertTrue(ctx.isClosed());
        assertTrue(ctx.getDataSourceProvider().get().isClosed());

        // Second close is idempotent
        assertDoesNotThrow(ctx::close);
        assertTrue(ctx.isClosed());
    }

    @Test
    @DisplayName("Diagnostic info returns sanitized details without credentials")
    void testDiagnosticInfoIsSanitized() {
        try (ApplicationContext memCtx = ApplicationContext.createInMemory()) {
            String diag = memCtx.getSafeDiagnosticInfo();
            assertTrue(diag.contains("IN_MEMORY"));
            assertFalse(diag.toLowerCase().contains("password"));
        }

        assumeTrue(postgresAvailable, "PostgreSQL not running; skipping JDBC diagnostic check");
        try (ApplicationContext jdbcCtx = ApplicationContext.createJdbcTest(testDbConfig)) {
            String diag = jdbcCtx.getSafeDiagnosticInfo();
            assertTrue(diag.contains("JDBC_TEST"));
            assertFalse(diag.toLowerCase().contains("password"));
        }
    }
}
