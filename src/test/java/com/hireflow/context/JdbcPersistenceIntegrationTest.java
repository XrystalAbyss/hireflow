package com.hireflow.context;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.model.*;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.scheduler.SchedulingRequest;
import com.hireflow.scheduler.SchedulingResult;
import com.hireflow.scheduler.TimeWindow;
import com.hireflow.service.InterviewService;
import com.hireflow.service.SampleDataLoader;
import com.hireflow.ui.MainFrame;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Stage 2B.6: PostgreSQL Persistence & Multi-Context Verification Tests")
public class JdbcPersistenceIntegrationTest {

    private static DatabaseConfig testConfig;
    private static boolean dbAvailable = false;

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

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(5)
                .minimumIdle(1)
                .connectionTimeoutMs(5000)
                .build();

        try (com.hireflow.db.DataSourceProvider dsp = new com.hireflow.db.DataSourceProvider(testConfig)) {
            dbAvailable = dsp.verifyConnectionHealth(3);
            if (dbAvailable) {
                FlywayMigrationService fms = new FlywayMigrationService(dsp.getDataSource());
                fms.migrate();
            }
        } catch (Exception ex) {
            dbAvailable = false;
        }

        if (dbAvailable) {
            cleanTestPrefixData();
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dbAvailable) {
            cleanTestPrefixData();
        }
    }

    private static void cleanTestPrefixData() {
        try (com.hireflow.db.DataSourceProvider dsp = new com.hireflow.db.DataSourceProvider(testConfig);
             Connection conn = dsp.getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.executeUpdate("DELETE FROM interview_sessions WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM applications WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM recruitment_drives WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM interviewers WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM companies WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM rooms WHERE id LIKE 'TEST-%'");
            stmt.executeUpdate("DELETE FROM students WHERE id LIKE 'TEST-%'");
            conn.commit();
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(1)
    @DisplayName("Persistence 1: Student records persist across separate application contexts")
    void testStudentPersistenceAcrossContexts() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        // Context 1: Register student and close
        try (ApplicationContext ctx1 = ApplicationContext.createJdbcTest(testConfig)) {
            Student student = ctx1.getStudentService().registerStudent(
                    "TEST-STU-001", "Context Persistence Student", "ctx.stu@univ.edu", "CSE", 9.45, 2026, true
            );
            assertNotNull(student);
            assertEquals("TEST-STU-001", student.getId());
        }

        // Context 2: Reopen and verify student was persisted in PostgreSQL
        try (ApplicationContext ctx2 = ApplicationContext.createJdbcTest(testConfig)) {
            Optional<Student> opt = ctx2.getStudentRepository().findById("TEST-STU-001");
            assertTrue(opt.isPresent(), "Student must persist across separate ApplicationContext lifecycles");
            Student s = opt.get();
            assertEquals("Context Persistence Student", s.getName());
            assertEquals("ctx.stu@univ.edu", s.getEmail());
            assertEquals("CSE", s.getBranch());
            assertEquals(9.45, s.getCgpa(), 0.001);
            assertEquals(2026, s.getGraduationYear());
            assertTrue(s.isActive());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Persistence 2: Company and Recruitment Drive records persist across separate application contexts")
    void testCompanyAndDrivePersistenceAcrossContexts() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        LocalDate deadline = LocalDate.now().plusDays(20);

        // Context 1: Save Company and Drive and close
        try (ApplicationContext ctx1 = ApplicationContext.createJdbcTest(testConfig)) {
            Company company = new Company("TEST-CMP-001", "Persistence Tech", "Cloud Architecture");
            ctx1.getCompanyRepository().add(company);

            RecruitmentDrive drive = ctx1.getDriveService().createDrive(
                    "TEST-DRV-001", "TEST-CMP-001", "Principal SDE", 8.50, Set.of("CSE", "IT"), deadline
            );
            assertNotNull(drive);
        }

        // Context 2: Reopen and verify Company and Drive
        try (ApplicationContext ctx2 = ApplicationContext.createJdbcTest(testConfig)) {
            Optional<Company> compOpt = ctx2.getCompanyRepository().findById("TEST-CMP-001");
            assertTrue(compOpt.isPresent(), "Company must persist across separate contexts");
            assertEquals("Persistence Tech", compOpt.get().getName());
            assertEquals("Cloud Architecture", compOpt.get().getIndustry());

            Optional<RecruitmentDrive> drvOpt = ctx2.getDriveRepository().findById("TEST-DRV-001");
            assertTrue(drvOpt.isPresent(), "Recruitment Drive must persist across separate contexts");
            RecruitmentDrive d = drvOpt.get();
            assertEquals("TEST-CMP-001", d.getCompanyId());
            assertEquals("Principal SDE", d.getJobTitle());
            assertEquals(8.50, d.getMinimumCgpa(), 0.001);
            assertEquals(deadline, d.getApplicationDeadline());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Persistence 3: Eligible branch sets persist correctly across application contexts and updates")
    void testEligibleBranchesPersistenceAcrossContexts() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        // Context 1: Verify branches and update to CSE, IT, ECE
        try (ApplicationContext ctx1 = ApplicationContext.createJdbcTest(testConfig)) {
            RecruitmentDrive d = ctx1.getDriveService().getDriveOrThrow("TEST-DRV-001");
            assertEquals(Set.of("CSE", "IT"), d.getEligibleBranches());

            d.setEligibleBranches(Set.of("CSE", "IT", "ECE"));
            ctx1.getDriveRepository().update(d);
        }

        // Context 2: Reopen and verify updated branch set
        try (ApplicationContext ctx2 = ApplicationContext.createJdbcTest(testConfig)) {
            RecruitmentDrive d = ctx2.getDriveService().getDriveOrThrow("TEST-DRV-001");
            assertEquals(Set.of("CSE", "IT", "ECE"), d.getEligibleBranches(),
                    "Multi-table branch set must persist across separate application contexts");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Persistence 4: Applications and statuses persist across separate application contexts")
    void testApplicationLifecyclePersistenceAcrossContexts() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        // Context 1: Submit application and shortlist it
        try (ApplicationContext ctx1 = ApplicationContext.createJdbcTest(testConfig)) {
            Application app = ctx1.getApplicationService().submitApplication("TEST-STU-001", "TEST-DRV-001");
            assertEquals(ApplicationStatus.APPLIED, app.getStatus());

            ctx1.getApplicationService().shortlistApplication(app.getId());
            assertEquals(ApplicationStatus.SHORTLISTED, ctx1.getApplicationService().getApplicationOrThrow(app.getId()).getStatus());
        }

        // Context 2: Reopen and verify application status
        try (ApplicationContext ctx2 = ApplicationContext.createJdbcTest(testConfig)) {
            List<Application> apps = ctx2.getApplicationRepository().findByDriveId("TEST-DRV-001");
            assertEquals(1, apps.size());
            Application app = apps.get(0);
            assertEquals("TEST-STU-001", app.getStudentId());
            assertEquals("TEST-DRV-001", app.getDriveId());
            assertEquals(ApplicationStatus.SHORTLISTED, app.getStatus(),
                    "Application status must remain SHORTLISTED across separate application contexts");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Persistence 5: Confirmed interview appointments persist across separate application contexts")
    void testConfirmedInterviewPersistenceAcrossContexts() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        String appId;
        LocalDateTime slotStart = LocalDateTime.of(LocalDate.now().plusDays(2), LocalTime.of(10, 0));
        LocalDateTime slotEnd = slotStart.plusMinutes(45);

        // Context 1: Register interviewer, room, schedule, and publish
        try (ApplicationContext ctx1 = ApplicationContext.createJdbcTest(testConfig)) {
            ctx1.getInterviewerRepository().add(new Interviewer("TEST-INT-001", "Dr. Persist", "TEST-CMP-001"));
            ctx1.getRoomRepository().add(new Room("TEST-RM-001", "Lab 101", 4));

            List<Application> apps = ctx1.getApplicationRepository().findByDriveId("TEST-DRV-001");
            appId = apps.get(0).getId();

            InterviewSession session = new InterviewSession(
                    "TEST-SES-001",
                    appId,
                    "TEST-INT-001",
                    "TEST-RM-001",
                    slotStart,
                    slotEnd
            );

            var pubResult = ctx1.getInterviewService().publishSchedule(List.of(session), Duration.ofMinutes(15));
            assertEquals(1, pubResult.publishedCount());
            assertTrue(pubResult.conflictRejections().isEmpty());
            assertEquals(ApplicationStatus.SCHEDULED, ctx1.getApplicationService().getApplicationOrThrow(appId).getStatus());
        }

        // Context 2: Reopen and verify interview session appointment and application status
        try (ApplicationContext ctx2 = ApplicationContext.createJdbcTest(testConfig)) {
            Optional<InterviewSession> sessOpt = ctx2.getSessionRepository().findById("TEST-SES-001");
            assertTrue(sessOpt.isPresent(), "InterviewSession must persist across context restarts");
            InterviewSession s = sessOpt.get();
            assertEquals("TEST-INT-001", s.getInterviewerId());
            assertEquals("TEST-RM-001", s.getRoomId());
            assertEquals(slotStart, s.getStartTime());
            assertEquals(slotEnd, s.getEndTime());

            // Application status must be SCHEDULED in the new context
            Application app = ctx2.getApplicationRepository().findById(s.getApplicationId()).orElseThrow();
            assertEquals(ApplicationStatus.SCHEDULED, app.getStatus());
        }
    }

    @Test
    @Order(6)
    @DisplayName("Persistence 6: Dashboard totals match database records")
    void testDashboardTotalsMatchDatabase() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        try (ApplicationContext ctx = ApplicationContext.createJdbcTest(testConfig);
             Connection conn = ctx.getDataSourceProvider().get().getConnection();
             Statement stmt = conn.createStatement()) {

            int uiStudents = ctx.getStudentService().getTotalStudentsCount();
            int uiCompanies = ctx.getCompanyRepository().count();
            int uiDrives = ctx.getDriveRepository().count();
            int uiApps = ctx.getApplicationService().getAllApplications().size();
            int uiSessions = ctx.getSessionRepository().count();

            ResultSet rsStudents = stmt.executeQuery("SELECT count(*) FROM students");
            rsStudents.next();
            assertEquals(rsStudents.getInt(1), uiStudents, "Dashboard student total must match DB row count");

            ResultSet rsCompanies = stmt.executeQuery("SELECT count(*) FROM companies");
            rsCompanies.next();
            assertEquals(rsCompanies.getInt(1), uiCompanies, "Dashboard company total must match DB row count");

            ResultSet rsDrives = stmt.executeQuery("SELECT count(*) FROM recruitment_drives");
            rsDrives.next();
            assertEquals(rsDrives.getInt(1), uiDrives, "Dashboard drive total must match DB row count");

            ResultSet rsApps = stmt.executeQuery("SELECT count(*) FROM applications");
            rsApps.next();
            assertEquals(rsApps.getInt(1), uiApps, "Dashboard application total must match DB row count");

            ResultSet rsSessions = stmt.executeQuery("SELECT count(*) FROM interview_sessions");
            rsSessions.next();
            assertEquals(rsSessions.getInt(1), uiSessions, "Dashboard session total must match DB row count");
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Persistence 7: Duplicate schedules are prevented after context restart")
    void testDuplicateSchedulePreventedAfterRestart() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        LocalDateTime slotStart = LocalDateTime.of(LocalDate.now().plusDays(2), LocalTime.of(10, 0));
        LocalDateTime slotEnd = slotStart.plusMinutes(45);

        try (ApplicationContext ctx = ApplicationContext.createJdbcTest(testConfig)) {
            List<Application> apps = ctx.getApplicationRepository().findByDriveId("TEST-DRV-001");
            String appId = apps.get(0).getId();

            // Attempt to publish another session for the already scheduled application
            InterviewSession duplicateSession = new InterviewSession(
                    "TEST-SES-002",
                    appId,
                    "TEST-INT-001",
                    "TEST-RM-001",
                    slotStart.plusHours(2),
                    slotEnd.plusHours(2)
            );

            var pubResult = ctx.getInterviewService().publishSchedule(List.of(duplicateSession), Duration.ofMinutes(15));
            assertEquals(0, pubResult.publishedCount(), "Must not publish duplicate appointment for already scheduled application");
            assertTrue(pubResult.skippedDuplicateApplicationIds().contains(appId.toUpperCase()));
        }
    }

    @Test
    @Order(8)
    @DisplayName("Persistence 8: No repeated automatic demo seeding when database is already populated")
    void testNoRepeatedAutomaticDemoSeeding() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        try (ApplicationContext ctx = ApplicationContext.createJdbcTest(testConfig)) {
            SampleDataLoader loader = new SampleDataLoader(
                    ctx.getStudentRepository(),
                    ctx.getCompanyRepository(),
                    ctx.getDriveRepository(),
                    ctx.getInterviewerRepository(),
                    ctx.getRoomRepository()
            );

            // Since database already has TEST-* records, seedIfEmpty should safely skip
            boolean didSeed = loader.seedIfEmpty();
            assertFalse(didSeed, "seedIfEmpty must return false and skip seeding when database is populated");
        }
    }

    @Test
    @Order(9)
    @DisplayName("Persistence 9: Swing MainFrame initializes and navigates without EDT exceptions")
    void testMainFrameGuiNavigationWithApplicationContext() {
        assumeTrue(dbAvailable, "PostgreSQL not running on hireflow_test");

        try (ApplicationContext ctx = ApplicationContext.createJdbcTest(testConfig)) {
            MainFrame frame = new MainFrame(ctx);
            assertNotNull(frame);
            assertTrue(frame.getTitle().contains("PostgreSQL"));

            // Test navigation across all screens
            assertDoesNotThrow(() -> {
                frame.switchScreen(MainFrame.SCREEN_STUDENTS);
                frame.switchScreen(MainFrame.SCREEN_DRIVES);
                frame.switchScreen(MainFrame.SCREEN_APPLICATIONS);
                frame.switchScreen(MainFrame.SCREEN_SCHEDULE);
                frame.switchScreen(MainFrame.SCREEN_DASHBOARD);
                frame.refreshAllPanels();
            });

            frame.dispose();
        }
    }
}
