package com.hireflow.web.integration;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.model.*;
import com.hireflow.web.security.UserRole;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Stage 3G: End-to-End Tomcat & PostgreSQL Acceptance Workflow")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TomcatPostgresE2EWorkflowTest {

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
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", com.hireflow.testsupport.CoreTestDatabase.url())));
        DB_USER = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        DB_PASS = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));
    }

    private static boolean dbAvailable = false;
    private static DatabaseConfig testConfig;

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
                    .maximumPoolSize(5)
                    .minimumIdle(1)
                    .connectionTimeoutMs(5000)
                    .baselineOnMigrate(true)
                    .build();
            cleanAllE2eRecords();
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        cleanAllE2eRecords();
        System.clearProperty("hireflow.allow.test.context");
        System.clearProperty("hireflow.storage.mode");
    }

    @BeforeEach
    void setUp() {
        assumeTrue(dbAvailable, "PostgreSQL test database not accessible at " + DB_URL);
        cleanAllE2eRecords();
    }

    @AfterEach
    void tearDown() {
        cleanAllE2eRecords();
    }

    private static void cleanAllE2eRecords() {
        com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
    }

    private static String extractCsrfToken(String html) {
        Pattern p1 = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
        Matcher m1 = p1.matcher(html);
        if (m1.find()) return m1.group(1);

        Pattern p2 = Pattern.compile("value=\"([^\"]+)\"\\s+name=\"_csrf\"");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1);

        Pattern p3 = Pattern.compile("<meta\\s+name=\"csrf-token\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m3 = p3.matcher(html);
        if (m3.find()) return m3.group(1);

        Pattern p4 = Pattern.compile("<meta\\s+content=\"([^\"]+)\"\\s+name=\"csrf-token\"", Pattern.CASE_INSENSITIVE);
        Matcher m4 = p4.matcher(html);
        if (m4.find()) return m4.group(1);

        Pattern p5 = Pattern.compile("<meta\\s+name=\"_csrf\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m5 = p5.matcher(html);
        if (m5.find()) return m5.group(1);

        throw new IllegalStateException("Could not extract CSRF token from HTML response:\n" + html.substring(0, Math.min(html.length(), 500)));
    }

    private static String extractPreviewId(String html) {
        Pattern pattern = Pattern.compile("name=\"previewId\"\\s+value=\"(PREV-[A-Fa-f0-9]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) return matcher.group(1);
        Pattern tokenPattern = Pattern.compile("(PREV-[A-Fa-f0-9]+)");
        Matcher tokenMatcher = tokenPattern.matcher(html);
        if (tokenMatcher.find()) return tokenMatcher.group(1);
        throw new IllegalStateException("Could not find previewId in response HTML");
    }

    private Tomcat startTomcatServer() throws Exception {
        System.setProperty("hireflow.storage.mode", "JDBC_TEST");
        System.setProperty("hireflow.db.url", DB_URL);
        System.setProperty("hireflow.db.user", DB_USER);
        System.setProperty("hireflow.db.password", DB_PASS);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir("target/tomcat-e2e-test");
        tomcat.setPort(0);
        tomcat.getConnector();

        File webappDir = new File("src/main/webapp");
        Context ctx = tomcat.addWebapp("/hireflow", webappDir.getAbsolutePath());
        ctx.addParameter("hireflow.storage.mode", "JDBC_TEST");

        tomcat.start();
        return tomcat;
    }

    @Test
    @DisplayName("Complete 20-Step Browser Acceptance Workflow against Embedded Tomcat & PostgreSQL")
    void testCompleteBrowserAcceptanceWorkflow() throws Exception {
        // Seed initial student record and user accounts in PostgreSQL
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) VALUES " +
                    "('E2E-STU-01', 'Alan Turing', 'alan@e2e.edu', 'CSE', 9.50, 2026, TRUE)");
        }

        // Provision officer and student accounts via temporary ApplicationContext
        try (ApplicationContext initCtx = ApplicationContext.create(testConfig)) {
            initCtx.getAuthenticationService().provisionAccount(
                    "e2e.officer", "OfficerPass123!".toCharArray(), UserRole.PLACEMENT_OFFICER, null, "E2E Officer"
            );
            initCtx.getAuthenticationService().provisionAccount(
                    "e2e.student", "StudentPass123!".toCharArray(), UserRole.STUDENT, "E2E-STU-01", "Alan Turing"
            );
        }

        Tomcat tomcat = startTomcatServer();
        int port = tomcat.getConnector().getLocalPort();
        String baseUrl = "http://127.0.0.1:" + port + "/hireflow";

        try {
            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            // 1. Public recruitment catalog loads
            HttpResponse<String> step1Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/drives")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step1Resp.statusCode(), "Step 1: Public drives catalog must return 200");
            assertTrue(step1Resp.body().contains("Recruitment Drives") || step1Resp.body().contains("Placement Drives"));

            // 2. Placement officer logs in securely
            HttpResponse<String> loginPageResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String loginCsrf = extractCsrfToken(loginPageResp.body());

            String officerLoginData = "username=e2e.officer&password=" + URLEncoder.encode("OfficerPass123!", StandardCharsets.UTF_8)
                    + "&_csrf=" + URLEncoder.encode(loginCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step2Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(officerLoginData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step2Resp.statusCode(), "Step 2: Officer login must succeed");
            assertTrue(step2Resp.body().contains("Officer Dashboard") || step2Resp.body().contains("E2E Officer") || step2Resp.body().contains("Welcome"));

            // 3. Officer creates a company
            HttpResponse<String> compFormResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/companies")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String compCsrf = extractCsrfToken(compFormResp.body());

            String createCompData = "id=E2E-CMP-01&name=Cyberdyne+Systems&industry=Robotics&_csrf=" + URLEncoder.encode(compCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step3Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/companies"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(createCompData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step3Resp.statusCode(), "Step 3: Company creation must succeed");
            assertTrue(step3Resp.body().contains("Cyberdyne Systems"));

            // 4. Officer creates a recruitment drive with eligible branches
            HttpResponse<String> driveFormResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/drives")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String driveCsrf = extractCsrfToken(driveFormResp.body());

            String createDriveData = "id=E2E-DRV-01&companyId=E2E-CMP-01&jobTitle=Autonomous+Systems+Engineer"
                    + "&minimumCgpa=8.0&eligibleBranches=CSE&applicationDeadline=" + LocalDate.now().plusDays(14)
                    + "&_csrf=" + URLEncoder.encode(driveCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step4Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/drives"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(createDriveData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step4Resp.statusCode(), "Step 4: Recruitment drive creation must succeed");
            assertTrue(step4Resp.body().contains("Autonomous Systems Engineer"));

            // 5. Student logs in with a separate account (new cookie manager)
            CookieManager studentCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient studentClient = HttpClient.newBuilder()
                    .cookieHandler(studentCookieManager)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> stuLoginPage = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String stuLoginCsrf = extractCsrfToken(stuLoginPage.body());

            String stuLoginData = "username=e2e.student&password=" + URLEncoder.encode("StudentPass123!", StandardCharsets.UTF_8)
                    + "&_csrf=" + URLEncoder.encode(stuLoginCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step5Resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(stuLoginData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step5Resp.statusCode(), "Step 5: Student login must succeed");
            assertTrue(step5Resp.body().contains("Student Dashboard") || step5Resp.body().contains("Alan Turing"));

            // 6. Student views their own profile
            HttpResponse<String> step6Resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/profile")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step6Resp.statusCode(), "Step 6: Student profile must return 200");
            assertTrue(step6Resp.body().contains("Alan Turing"));
            assertTrue(step6Resp.body().contains("E2E-STU-01"));
            assertTrue(step6Resp.body().contains("CSE"));

            // 7. Student receives the correct eligibility decision
            HttpResponse<String> step7Resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step7Resp.statusCode(), "Step 7: Student drives list must return 200");
            assertTrue(step7Resp.body().contains("Autonomous Systems Engineer"));
            assertTrue(step7Resp.body().contains("ELIGIBLE") || step7Resp.body().contains("Eligible"));

            // 8. Student applies to the drive
            String applyCsrf = extractCsrfToken(step7Resp.body());
            String applyData = "driveId=E2E-DRV-01&_csrf=" + URLEncoder.encode(applyCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step8Resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/applications"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(applyData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step8Resp.statusCode(), "Step 8: Application submission must succeed");
            assertTrue(step8Resp.body().contains("Autonomous Systems Engineer"));
            assertTrue(step8Resp.body().contains("APPLIED") || step8Resp.body().contains("Applied"));

            // Determine application ID from database
            String appId;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM applications WHERE student_id = 'E2E-STU-01' AND drive_id = 'E2E-DRV-01'")) {
                assertTrue(rs.next(), "Application must exist in PostgreSQL");
                appId = rs.getString("id");
            }

            // 9. Officer reviews the application
            HttpResponse<String> step9Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/applications?driveId=E2E-DRV-01")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step9Resp.statusCode(), "Step 9: Officer applications view must return 200");
            assertTrue(step9Resp.body().contains("Alan Turing") || step9Resp.body().contains("E2E-STU-01"));

            // 10. Officer shortlists the candidate
            String shortlistCsrf = extractCsrfToken(step9Resp.body());
            String shortlistData = "_csrf=" + URLEncoder.encode(shortlistCsrf, StandardCharsets.UTF_8);
            HttpResponse<String> step10Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/applications/" + appId + "/shortlist"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(shortlistData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step10Resp.statusCode(), "Step 10: Shortlisting must succeed");
            assertTrue(step10Resp.body().contains("SHORTLISTED") || step10Resp.body().contains("shortlisted"));

            // 11. Officer configures interviewer and room resources
            HttpResponse<String> intFormResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/interviewers")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String intCsrf = extractCsrfToken(intFormResp.body());
            String createIntData = "id=E2E-INT-01&name=Dr.+Sarah+Connor&companyId=E2E-CMP-01&_csrf=" + URLEncoder.encode(intCsrf, StandardCharsets.UTF_8);
            client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/interviewers"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(createIntData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            HttpResponse<String> roomFormResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/rooms")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String roomCsrf = extractCsrfToken(roomFormResp.body());
            String createRoomData = "id=E2E-RM-01&name=Auditorium+Alpha&capacity=1&_csrf=" + URLEncoder.encode(roomCsrf, StandardCharsets.UTF_8);
            client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/rooms"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(createRoomData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // 12. Officer generates a scheduling preview
            HttpResponse<String> schedFormResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling?driveId=E2E-DRV-01")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String schedCsrf = extractCsrfToken(schedFormResp.body());

            String previewData = "driveId=E2E-DRV-01"
                    + "&date=" + LocalDate.now().plusDays(2)
                    + "&startTime=09:00"
                    + "&endTime=12:00"
                    + "&duration=30"
                    + "&buffer=15"
                    + "&interviewerIds=E2E-INT-01"
                    + "&roomIds=E2E-RM-01"
                    + "&_csrf=" + URLEncoder.encode(schedCsrf, StandardCharsets.UTF_8);

            HttpResponse<String> step12Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling/preview"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(previewData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step12Resp.statusCode(), "Step 12: Preview generation must succeed");
            assertTrue(step12Resp.body().contains("ADVISORY PREVIEW") || step12Resp.body().contains("Advisory"));
            String previewId = extractPreviewId(step12Resp.body());
            assertNotNull(previewId);

            // 13. Preview does not modify the database
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                ResultSet rsSess = stmt.executeQuery("SELECT COUNT(*) FROM interview_sessions WHERE application_id = '" + appId + "'");
                rsSess.next();
                assertEquals(0, rsSess.getInt(1), "Step 13: Zero interview sessions must exist prior to publication");

                ResultSet rsApp = stmt.executeQuery("SELECT status FROM applications WHERE id = '" + appId + "'");
                rsApp.next();
                assertEquals("SHORTLISTED", rsApp.getString(1), "Step 13: Application status must remain SHORTLISTED during preview");
            }

            // 14. Officer publishes the proposed schedule
            String publishCsrf = extractCsrfToken(step12Resp.body());
            String publishData = "previewId=" + URLEncoder.encode(previewId, StandardCharsets.UTF_8)
                    + "&_csrf=" + URLEncoder.encode(publishCsrf, StandardCharsets.UTF_8);

            HttpResponse<String> step14Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling/publish"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(publishData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step14Resp.statusCode(), "Step 14: Publication POST must complete and redirect to published schedule");

            // 15. Application becomes SCHEDULED in PostgreSQL
            // 16. Confirmed session is stored in PostgreSQL
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                ResultSet rsApp = stmt.executeQuery("SELECT status FROM applications WHERE id = '" + appId + "'");
                rsApp.next();
                assertEquals("SCHEDULED", rsApp.getString(1), "Step 15: Application status must be SCHEDULED after publication");

                ResultSet rsSess = stmt.executeQuery("SELECT id, interviewer_id, room_id FROM interview_sessions WHERE application_id = '" + appId + "'");
                assertTrue(rsSess.next(), "Step 16: Confirmed interview session must exist in PostgreSQL");
                assertEquals("E2E-INT-01", rsSess.getString("interviewer_id"));
                assertEquals("E2E-RM-01", rsSess.getString("room_id"));
            }

            // 17. Student logs back in and sees only their own appointment
            HttpResponse<String> step17Resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/interviews")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step17Resp.statusCode(), "Step 17: Student interview schedule must return 200");
            assertTrue(step17Resp.body().contains("Autonomous Systems Engineer"));
            assertTrue(step17Resp.body().contains("Cyberdyne Systems"));
            assertTrue(step17Resp.body().contains("Auditorium Alpha"));

            // 18. Officer timetable reflects the same confirmed session
            HttpResponse<String> step18Resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling/published?driveId=E2E-DRV-01")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, step18Resp.statusCode(), "Step 18: Officer published timetable must return 200");
            assertTrue(step18Resp.body().contains("Alan Turing") || step18Resp.body().contains("E2E-STU-01"));
            assertTrue(step18Resp.body().contains("Auditorium Alpha"));

        } finally {
            // 19. Fully shut down and restart the web application
            tomcat.stop();
            tomcat.destroy();
        }

        // 20. Start fresh Tomcat instance and verify records survive the restart
        Tomcat restartedTomcat = startTomcatServer();
        int restartedPort = restartedTomcat.getConnector().getLocalPort();
        String restartedBaseUrl = "http://127.0.0.1:" + restartedPort + "/hireflow";

        try {
            // Check direct PostgreSQL persistence
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                ResultSet rsDrive = stmt.executeQuery("SELECT job_title FROM recruitment_drives WHERE id = 'E2E-DRV-01'");
                assertTrue(rsDrive.next(), "Step 20: Recruitment drive must survive Tomcat restart");
                assertEquals("Autonomous Systems Engineer", rsDrive.getString("job_title"));

                ResultSet rsApp = stmt.executeQuery("SELECT status FROM applications WHERE student_id = 'E2E-STU-01' AND drive_id = 'E2E-DRV-01'");
                assertTrue(rsApp.next(), "Step 20: Candidate application must survive Tomcat restart");
                assertEquals("SCHEDULED", rsApp.getString("status"));

                ResultSet rsSess = stmt.executeQuery("SELECT s.id FROM interview_sessions s JOIN applications a ON s.application_id = a.id WHERE a.student_id = 'E2E-STU-01'");
                assertTrue(rsSess.next(), "Step 20: Confirmed interview session must survive Tomcat restart");
            }

            // Verify over HTTP on restarted web application
            CookieManager restartedStudentCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient restartedStudentClient = HttpClient.newBuilder()
                    .cookieHandler(restartedStudentCookieManager)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> reLoginResp = restartedStudentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(restartedBaseUrl + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            String reCsrf = extractCsrfToken(reLoginResp.body());

            String stuLoginData = "username=e2e.student&password=" + URLEncoder.encode("StudentPass123!", StandardCharsets.UTF_8)
                    + "&_csrf=" + URLEncoder.encode(reCsrf, StandardCharsets.UTF_8);
            restartedStudentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(restartedBaseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(stuLoginData)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            HttpResponse<String> restartedInterviewsResp = restartedStudentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(restartedBaseUrl + "/student/interviews")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, restartedInterviewsResp.statusCode(), "Step 20: Student must see confirmed appointment on restarted server");
            assertTrue(restartedInterviewsResp.body().contains("Autonomous Systems Engineer"));
            assertTrue(restartedInterviewsResp.body().contains("Cyberdyne Systems"));
            assertTrue(restartedInterviewsResp.body().contains("Auditorium Alpha"));

        } finally {
            restartedTomcat.stop();
            restartedTomcat.destroy();
        }
    }
}
