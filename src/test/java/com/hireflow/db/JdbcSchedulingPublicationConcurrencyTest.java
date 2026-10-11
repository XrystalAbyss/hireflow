package com.hireflow.db;

import com.hireflow.context.ApplicationContext;
import com.hireflow.model.*;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.service.InterviewService;
import com.hireflow.web.security.UserRole;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("JdbcSchedulingPublicationConcurrencyTest - PostgreSQL Publication Concurrency and GiST Safety")
class JdbcSchedulingPublicationConcurrencyTest {

    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASS;

    static {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }
        DB_URL = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        DB_USER = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        DB_PASS = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));
    }

    private static boolean dbAvailable = false;
    private static DatabaseConfig testConfig;
    private ApplicationContext appContext;

    @BeforeAll
    static void checkDatabaseAvailability() {
        System.setProperty("hireflow.allow.test.context", "true");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            dbAvailable = true;
            testConfig = DatabaseConfig.builder()
                    .mode(DatabaseMode.JDBC_TEST)
                    .jdbcUrl(DB_URL)
                    .username(DB_USER)
                    .password(DB_PASS)
                    .maximumPoolSize(10)
                    .minimumIdle(2)
                    .connectionTimeoutMs(5000)
                    .baselineOnMigrate(true)
                    .build();
            cleanAllTestDataDirectSql();
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        cleanAllTestDataDirectSql();
        System.clearProperty("hireflow.allow.test.context");
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(dbAvailable, "PostgreSQL test database not accessible at " + DB_URL);
        cleanAllTestDataDirectSql();
        appContext = ApplicationContext.create(testConfig);
    }

    @AfterEach
    void tearDown() {
        if (appContext != null) {
            try {
                appContext.close();
            } catch (Exception ignored) {
            } finally {
                appContext = null;
            }
        }
        cleanAllTestDataDirectSql();
    }

    private static void cleanAllTestDataDirectSql() {
        if (!dbAvailable) return;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM interview_sessions WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM applications WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM interviewers WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM rooms WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM recruitment_drives WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM companies WHERE id LIKE 'PG-%'");
            stmt.executeUpdate("DELETE FROM students WHERE id LIKE 'PG-%'");
        } catch (Exception ignored) {
        }
    }

    private void seedBaseEntities() {
        appContext.getCompanyRepository().save(new Company("PG-CMP-01", "PostgreSQL Cloud", "Cloud"));
        appContext.getDriveRepository().save(new RecruitmentDrive("PG-DRV-01", "PG-CMP-01", "Database Engineer", 7.5, Set.of("CSE"), LocalDate.now().plusDays(10)));

        appContext.getStudentRepository().save(new Student("PG-STU-01", "Alice PG", "alice@pg.edu", "CSE", 8.9, 2026, true));
        appContext.getStudentRepository().save(new Student("PG-STU-02", "Bob PG", "bob@pg.edu", "CSE", 8.7, 2026, true));

        appContext.getInterviewerRepository().save(new Interviewer("PG-INT-01", "Dr. PostgreSQL", "PG-CMP-01"));
        appContext.getRoomRepository().save(new Room("PG-RM-01", "Database Lab", 1));

        appContext.getApplicationRepository().save(new Application("PG-APP-01", "PG-STU-01", "PG-DRV-01", ApplicationStatus.SHORTLISTED));
        appContext.getApplicationRepository().save(new Application("PG-APP-02", "PG-STU-02", "PG-DRV-01", ApplicationStatus.SHORTLISTED));
    }

    @Test
    @DisplayName("concurrency: two officers publishing overlapping schedules in PostgreSQL results in 1 winner and 1 conflict")
    void testConcurrentOverlappingPublicationInPostgreSql() throws Exception {
        seedBaseEntities();

        InterviewService interviewService = appContext.getInterviewService();

        LocalDateTime slotStart = LocalDateTime.of(2026, 11, 20, 10, 0);
        LocalDateTime slotEnd = slotStart.plusMinutes(45);

        // Propose overlapping room assignment: both want PG-RM-01 at 10:00 - 10:45
        InterviewSession session1 = new InterviewSession("PG-SESS-01", "PG-APP-01", "PG-INT-01", "PG-RM-01", slotStart, slotEnd);
        InterviewSession session2 = new InterviewSession("PG-SESS-02", "PG-APP-02", "PG-INT-01", "PG-RM-01", slotStart, slotEnd);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<InterviewService.PublicationResult> task1 = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return interviewService.publishSchedule(List.of(session1), Duration.ofMinutes(15));
        };

        Callable<InterviewService.PublicationResult> task2 = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return interviewService.publishSchedule(List.of(session2), Duration.ofMinutes(15));
        };

        Future<InterviewService.PublicationResult> f1 = executor.submit(task1);
        Future<InterviewService.PublicationResult> f2 = executor.submit(task2);

        InterviewService.PublicationResult res1 = f1.get(10, TimeUnit.SECONDS);
        InterviewService.PublicationResult res2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int totalPublished = res1.publishedCount() + res2.publishedCount();
        int totalConflicts = res1.conflictRejections().size() + res2.conflictRejections().size();

        assertEquals(1, totalPublished, "Exactly one publication must succeed under pessimistic PostgreSQL locks");
        assertEquals(1, totalConflicts, "The overlapping proposal must be detected and rejected as a conflict");

        // Verify Application statuses in PostgreSQL:
        // The winner moved to SCHEDULED, the loser remained SHORTLISTED
        Application app1 = appContext.getApplicationRepository().findById("PG-APP-01").orElseThrow();
        Application app2 = appContext.getApplicationRepository().findById("PG-APP-02").orElseThrow();

        if (res1.publishedCount() == 1) {
            assertEquals(ApplicationStatus.SCHEDULED, app1.getStatus());
            assertEquals(ApplicationStatus.SHORTLISTED, app2.getStatus(), "Failed publication candidate must remain SHORTLISTED");
        } else {
            assertEquals(ApplicationStatus.SHORTLISTED, app1.getStatus(), "Failed publication candidate must remain SHORTLISTED");
            assertEquals(ApplicationStatus.SCHEDULED, app2.getStatus());
        }

        // Verify in database: exactly 1 session row persisted
        List<InterviewSession> confirmed = appContext.getSessionRepository().findAll().stream()
                .filter(s -> s.getId().startsWith("PG-SESS-"))
                .toList();
        assertEquals(1, confirmed.size());
    }

    @Test
    @DisplayName("atomicity: partial publication commits feasible candidates and rejects conflicted candidates without rollback of feasible ones")
    void testPartialPublicationCommitAndReporting() {
        seedBaseEntities();

        InterviewService interviewService = appContext.getInterviewService();

        // Seed an existing confirmed appointment for PG-STU-01 during 10:00 - 10:45
        LocalDateTime t10 = LocalDateTime.of(2026, 11, 20, 10, 0);
        appContext.getRoomRepository().save(new Room("PG-RM-02", "Lab 2", 1));
        appContext.getInterviewerRepository().save(new Interviewer("PG-INT-02", "Interviewer 2", "PG-CMP-01"));

        InterviewSession existing = new InterviewSession("PG-SESS-01", "PG-APP-01", "PG-INT-01", "PG-RM-01", t10, t10.plusMinutes(45));
        interviewService.publishSchedule(List.of(existing), Duration.ofMinutes(15));
        assertEquals(ApplicationStatus.SCHEDULED, appContext.getApplicationRepository().findById("PG-APP-01").orElseThrow().getStatus());

        // Now propose 2 new sessions:
        // Candidate 1 (PG-APP-01): DUPLICATE / NOT SHORTLISTED -> will be rejected
        // Candidate 2 (PG-APP-02): Feasible at 11:30 in RM-02 with INT-02 -> will be published
        LocalDateTime t1130 = LocalDateTime.of(2026, 11, 20, 11, 30);
        InterviewSession proposedConflicted = new InterviewSession("PG-SESS-A", "PG-APP-01", "PG-INT-01", "PG-RM-01", t10, t10.plusMinutes(45));
        InterviewSession proposedFeasible = new InterviewSession("PG-SESS-B", "PG-APP-02", "PG-INT-02", "PG-RM-02", t1130, t1130.plusMinutes(45));

        InterviewService.PublicationResult result = interviewService.publishSchedule(
                List.of(proposedConflicted, proposedFeasible), Duration.ofMinutes(15)
        );

        assertEquals(1, result.publishedCount(), "Feasible proposed session must be published");
        assertTrue(result.skippedDuplicateApplicationIds().contains("PG-APP-01") || result.hasConflicts(),
                "Conflicted / duplicate proposal must be skipped or rejected");

        // Verify Candidate 2 is SCHEDULED
        assertEquals(ApplicationStatus.SCHEDULED, appContext.getApplicationRepository().findById("PG-APP-02").orElseThrow().getStatus());
        assertTrue(appContext.getSessionRepository().existsById("PG-SESS-B"));
    }

    @Test
    @DisplayName("persistence: confirmed interview session survives application context restart")
    void testPersistenceAcrossContextRestart() throws Exception {
        seedBaseEntities();

        InterviewService interviewService = appContext.getInterviewService();
        LocalDateTime t = LocalDateTime.of(2026, 11, 21, 14, 0);
        InterviewSession sess = new InterviewSession("PG-SESS-01", "PG-APP-01", "PG-INT-01", "PG-RM-01", t, t.plusMinutes(45));

        InterviewService.PublicationResult res = interviewService.publishSchedule(List.of(sess), Duration.ofMinutes(15));
        assertEquals(1, res.publishedCount());

        // Close ApplicationContext (simulates Tomcat server shutdown)
        appContext.close();
        appContext = null;

        // Reopen fresh ApplicationContext (simulates Tomcat server reboot)
        try (ApplicationContext restarted = ApplicationContext.create(testConfig)) {
            Optional<InterviewSession> reloaded = restarted.getSessionRepository().findById("PG-SESS-01");
            assertTrue(reloaded.isPresent(), "Interview session must persist across application context restarts");
            assertEquals("PG-APP-01", reloaded.get().getApplicationId());
            assertEquals("PG-INT-01", reloaded.get().getInterviewerId());
            assertEquals("PG-RM-01", reloaded.get().getRoomId());

            Application appReloaded = restarted.getApplicationRepository().findById("PG-APP-01").orElseThrow();
            assertEquals(ApplicationStatus.SCHEDULED, appReloaded.getStatus(),
                    "Application status must remain SCHEDULED across context restarts");

            // Student repository findByStudentId in JDBC mode
            List<InterviewSession> stuSessions = restarted.getSessionRepository().findByStudentId("PG-STU-01");
            assertEquals(1, stuSessions.size());
            assertEquals("PG-SESS-01", stuSessions.get(0).getId());
        }
    }
}
