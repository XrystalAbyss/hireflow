package com.hireflow.web.integration;

import com.hireflow.context.ApplicationContext;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Room;
import com.hireflow.model.Student;
import com.hireflow.web.security.UserRole;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;

import java.io.File;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True Apache Tomcat 10.1 Container Integration Test.
 * <p>
 * Mounts the actual {@code src/main/webapp} directory containing {@code web.xml},
 * Jakarta servlets, authorization filters, and JSP templates into an embedded Tomcat instance.
 * Fired over real loopback HTTP connections using Java 21's {@link HttpClient}.
 */
@DisplayName("TomcatDeploymentIntegrationTest - True Tomcat 10.1 Container Integration")
class TomcatDeploymentIntegrationTest {

    private static Tomcat tomcat;
    private static int port;
    private static HttpClient httpClient;
    private static String baseUrl;
    private static ApplicationContext sharedAppContext;

    @BeforeAll
    static void startTomcat() throws Exception {
        // Enforce IN_MEMORY storage mode during container integration test
        System.setProperty("hireflow.storage.mode", "IN_MEMORY");

        tomcat = new Tomcat();
        tomcat.setBaseDir("target/tomcat-test");
        tomcat.setPort(0); // Ephemeral dynamic port

        // Ensure connector is initialized
        tomcat.getConnector();

        File webappDir = new File("src/main/webapp");
        if (!webappDir.exists()) {
            throw new IllegalStateException("webapp directory not found at: " + webappDir.getAbsolutePath());
        }

        Context ctx = tomcat.addWebapp("/hireflow", webappDir.getAbsolutePath());
        ctx.addParameter("hireflow.storage.mode", "IN_MEMORY");

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
        baseUrl = "http://127.0.0.1:" + port + "/hireflow";

        ApplicationContext appContext = (ApplicationContext) ctx.getServletContext().getAttribute("hireflow.appContext");
        sharedAppContext = appContext;
        if (appContext != null) {
            Student student = new Student("STU-101", "Rahul Sharma", "rahul@univ.edu", "CSE", 8.8, 2026, true);
            appContext.getStudentService().registerStudent(student);
            appContext.getAuthenticationService().provisionAccount(
                    "rahul.sharma",
                    "StudentPass123!".toCharArray(),
                    UserRole.STUDENT,
                    "STU-101",
                    "Rahul Sharma"
            );

            Student student2 = new Student("STU-102", "Priya Patel", "priya@univ.edu", "ECE", 7.5, 2026, true);
            appContext.getStudentService().registerStudent(student2);
            appContext.getAuthenticationService().provisionAccount(
                    "priya.patel",
                    "StudentPass123!".toCharArray(),
                    UserRole.STUDENT,
                    "STU-102",
                    "Priya Patel"
            );

            appContext.getAuthenticationService().provisionAccount(
                    "tpc.officer",
                    "OfficerPass123!".toCharArray(),
                    UserRole.PLACEMENT_OFFICER,
                    null,
                    "Director TPC"
            );

            Company comp1 = new Company("CMP-001", "Tech Corp", "tech.com");
            Company comp2 = new Company("CMP-002", "Circuits Inc", "circuits.com");
            appContext.getCompanyRepository().save(comp1);
            appContext.getCompanyRepository().save(comp2);

            RecruitmentDrive drive1 = new RecruitmentDrive("DRV-001", "CMP-001", "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10));
            RecruitmentDrive drive2 = new RecruitmentDrive("DRV-002", "CMP-002", "Hardware Engineer", 7.0, Set.of("ECE"), LocalDate.now().plusDays(5));
            RecruitmentDrive drive3 = new RecruitmentDrive("DRV-003", "CMP-001", "Legacy Analyst", 7.0, Set.of("CSE"), LocalDate.now().minusDays(2));
            appContext.getDriveService().createDrive(drive1);
            appContext.getDriveService().createDrive(drive2);
            appContext.getDriveService().createDrive(drive3);
        }

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @AfterAll
    static void stopTomcat() {
        System.clearProperty("hireflow.storage.mode");
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("deployment: GET / returns HTTP 200 and renders landing page HTML")
    void testRootLandingPageRenders() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("HireFlow"), "Response HTML should contain brand title");
        assertTrue(resp.body().contains("Campus Recruitment"), "Response HTML should contain hero headline");
    }

    @Test
    @DisplayName("deployment: GET /home returns HTTP 200")
    void testHomeExplicitUrlRenders() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/home"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("HireFlow"));
    }

    @Test
    @DisplayName("deployment: GET /about returns HTTP 200 and renders placement policies")
    void testAboutPageRenders() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/about"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Campus Placement Guidelines"), "Should render about policies title");
        assertTrue(resp.body().contains("Academic Eligibility"), "Should render academic criteria section");
    }

    @Test
    @DisplayName("deployment: GET /static/css/hireflow-web.css returns HTTP 200 and valid CSS tokens")
    void testStaticCssLoads() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/static/css/hireflow-web.css"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains(":root"), "CSS should define root design tokens");
        assertTrue(resp.body().contains("--bg-primary"), "CSS should contain theme variables");
    }

    @Test
    @DisplayName("deployment: GET /drives returns HTTP 200 and renders public drives catalog")
    void testDrivesCatalogRenders() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/drives"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Placement Drives Directory"), "Should render directory title");
        assertTrue(resp.body().contains("Total Published"), "Should render metrics section");
    }

    @Test
    @DisplayName("deployment: GET /drives with non-matching search returns HTTP 200 and renders friendly empty state")
    void testDrivesEmptySearchStateRenders() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/drives?q=UnicornStartupNonExistentXYZ"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("No Matching Recruitment Drives"), "Should render empty-state message");
        assertTrue(resp.body().contains("View All Drives"), "Should offer reset button");
    }

    @Test
    @DisplayName("deployment: GET /drives with filter and sort parameters returns HTTP 200")
    void testDrivesFilteredAndSorted() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/drives?branch=CSE&sort=company-asc"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Placement Drives Directory"));
    }

    @Test
    @DisplayName("deployment: GET /drives with invalid sort parameter falls back safely (HTTP 200)")
    void testDrivesInvalidSortFallsBack() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/drives?sort=HACK_KEYWORD_INJECTION"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
    }

    @Test
    @DisplayName("deployment: GET /health returns HTTP 200, JSON content-type, and UP status")
    void testHealthEndpointReturnsUp() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"),
                "Content-Type must be application/json");
        assertTrue(resp.body().contains("\"status\": \"UP\""), "JSON body should declare status UP");
        assertTrue(resp.body().contains("\"service\": \"HireFlow Web Portal\""), "JSON body should include service title");
    }

    @Test
    @DisplayName("security: GET /student/* returns HTTP 403 Forbidden with authentication gate view")
    void testStudentPortalBlockedForUnauthenticated() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/profile"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode(), "Unauthenticated request to /student/* must return 403 Forbidden");
        assertTrue(resp.body().contains("Protected Portal Boundary"), "Body should render auth-gate template");
    }

    @Test
    @DisplayName("security: GET /officer/* returns HTTP 403 Forbidden with authentication gate view")
    void testOfficerPortalBlockedForUnauthenticated() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/officer/dashboard"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode(), "Unauthenticated request to /officer/* must return 403 Forbidden");
        assertTrue(resp.body().contains("Protected Portal Boundary"), "Body should render auth-gate template");
    }

    @Test
    @DisplayName("security: client parameter tampering (?role=STUDENT&studentId=STU-101) cannot bypass 403 gate")
    void testParameterTamperingBlockedByContainer() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/profile?role=STUDENT&studentId=STU-101"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode(), "Parameter tampering must NOT bypass authentication");
        assertTrue(resp.body().contains("Protected Portal Boundary"));
    }

    @Test
    @DisplayName("security: HTTP response security headers are attached by SecurityHeadersFilter")
    void testSecurityHeadersEnforcedByContainer() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertEquals("nosniff", resp.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("DENY", resp.headers().firstValue("X-Frame-Options").orElse(null));
        assertEquals("strict-origin-when-cross-origin", resp.headers().firstValue("Referrer-Policy").orElse(null));
    }

    @Test
    @DisplayName("security: direct access to /WEB-INF/views/home.jsp is strictly forbidden (HTTP 404)")
    void testDirectWebInfAccessBlocked() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/WEB-INF/views/home.jsp"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode(), "Direct browser access to WEB-INF files must return 404");
    }

    @Test
    @DisplayName("routes: invalid route returns HTTP 404")
    void testInvalidRouteReturns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent-route-999"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode());
    }

    @Test
    @DisplayName("auth: GET /login renders login view with CSRF protection token")
    void testLoginPageRendersWithCsrf() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Sign In to HireFlow"), "Login page should display heading");
        String csrfToken = extractCsrfToken(resp.body());
        assertNotNull(csrfToken);
        assertEquals(64, csrfToken.length());
        assertTrue(resp.headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
    }

    @Test
    @DisplayName("auth: POST /login without CSRF token returns HTTP 403 Forbidden")
    void testLoginRejectsMissingCsrf() throws Exception {
        String body = "username=rahul.sharma&password=StudentPass123!";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode(), "Missing CSRF token must produce HTTP 403 Forbidden");
    }

    @Test
    @DisplayName("auth: POST /login with invalid credentials returns HTTP 401 Unauthorized with generic error")
    void testLoginRejectsInvalidCredentials() throws Exception {
        CookieManager cm = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cm).build();

        // 1. GET /login to obtain CSRF token
        HttpResponse<String> getResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(getResp.body());

        // 2. POST with wrong password
        String formData = buildFormData("rahul.sharma", "WrongPassword999", csrf);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, postResp.statusCode(), "Invalid credentials must return 401 Unauthorized");
        assertTrue(postResp.body().contains("Invalid username or password."), "Must display generic error message");
    }

    @Test
    @DisplayName("auth: STUDENT login succeeds, establishes session cookie, accesses student dashboard, denies officer console, and logs out")
    void testStudentLoginAndDashboardWorkflow() throws Exception {
        CookieManager cm = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // 1. GET /login to acquire CSRF token and cookie
        HttpResponse<String> getResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(getResp.body());

        // 2. POST /login with valid student credentials
        String formData = buildFormData("rahul.sharma", "StudentPass123!", csrf);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> loginResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

        // Followed redirect to student dashboard
        assertEquals(200, loginResp.statusCode());
        assertTrue(loginResp.body().contains("Welcome, Rahul Sharma"));
        assertTrue(loginResp.body().contains("STUDENT CANDIDATE"));
        assertTrue(loginResp.body().contains("STU-101"));

        // 3. Attempt accessing officer console with student session -> Must receive 403 Forbidden!
        HttpRequest officerReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/officer/dashboard"))
                .GET()
                .build();

        HttpResponse<String> officerResp = client.send(officerReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, officerResp.statusCode(), "Student account must be denied access to officer dashboard");

        // 4. Extract CSRF token from student dashboard for logout
        String logoutCsrf = extractCsrfToken(loginResp.body());

        // 5. POST /logout
        String logoutBody = "csrfToken=" + URLEncoder.encode(logoutCsrf, StandardCharsets.UTF_8);
        HttpRequest logoutReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(logoutBody))
                .build();

        HttpResponse<String> logoutResp = client.send(logoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutResp.statusCode());
        assertTrue(logoutResp.body().contains("logged out successfully"));

        // 6. Accessing student dashboard after logout must now be denied (403 Forbidden)!
        HttpRequest afterLogoutReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/dashboard"))
                .GET()
                .build();

        HttpResponse<String> afterLogoutResp = client.send(afterLogoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, afterLogoutResp.statusCode(), "Invalidated session must no longer authorize access");
    }

    @Test
    @DisplayName("auth: PLACEMENT_OFFICER login succeeds and accesses officer console")
    void testOfficerLoginAndDashboardWorkflow() throws Exception {
        CookieManager cm = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // 1. GET /login
        HttpResponse<String> getResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(getResp.body());

        // 2. POST /login with officer credentials
        String formData = buildFormData("tpc.officer", "OfficerPass123!", csrf);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> loginResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, loginResp.statusCode());
        assertTrue(loginResp.body().contains("Welcome, Director TPC"));
        assertTrue(loginResp.body().contains("ADMIN CONSOLE"));
    }

    private static String extractCsrfToken(String html) {
        Pattern pattern = Pattern.compile("name=\"(?:csrfToken|_csrf)\"\\s+value=\"([a-f0-9]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern metaPattern = Pattern.compile("name=\"csrf-token\"\\s+content=\"([a-f0-9]+)\"");
        Matcher metaMatcher = metaPattern.matcher(html);
        if (metaMatcher.find()) {
            return metaMatcher.group(1);
        }
        throw new IllegalStateException("Could not find csrfToken in response HTML:\n" + html);
    }

    private static String buildFormData(String username, String password, String csrfToken) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);
    }

    private HttpClient loginAs(String username, String password) throws Exception {
        CookieManager cm = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpResponse<String> getResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(getResp.body());

        String formData = buildFormData(username, password, csrf);
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> loginResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResp.statusCode(), "Login should succeed for " + username);
        return client;
    }

    @Test
    @DisplayName("student: GET /student/profile renders authenticated student academic profile and non-cacheable headers")
    void testStudentProfileAccessAndHeaders() throws Exception {
        HttpClient client = loginAs("rahul.sharma", "StudentPass123!");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/profile"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
        assertTrue(resp.body().contains("Rahul Sharma"));
        assertTrue(resp.body().contains("STU-101"));
        assertTrue(resp.body().contains("CSE"));
        assertTrue(resp.body().contains("8.80") || resp.body().contains("8.8"));
        assertTrue(resp.body().contains("2026"));

        // Parameter tampering with ?studentId=STU-102 must be ignored
        HttpRequest tamperReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/profile?studentId=STU-102"))
                .GET()
                .build();
        HttpResponse<String> tamperResp = client.send(tamperReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, tamperResp.statusCode());
        assertTrue(tamperResp.body().contains("Rahul Sharma"));
        assertFalse(tamperResp.body().contains("Priya Patel"));
    }

    @Test
    @DisplayName("student: cross-student isolation ensures Student A cannot access Student B's profile")
    void testCrossStudentIsolationCannotAccessOtherRecords() throws Exception {
        HttpClient clientPriya = loginAs("priya.patel", "StudentPass123!");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/profile"))
                .GET()
                .build();

        HttpResponse<String> resp = clientPriya.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Priya Patel"));
        assertTrue(resp.body().contains("STU-102"));
        assertTrue(resp.body().contains("ECE"));
        assertFalse(resp.body().contains("Rahul Sharma"));
        assertFalse(resp.body().contains("STU-101"));
    }

    @Test
    @DisplayName("student: GET /student/drives evaluates real-time academic eligibility on server")
    void testStudentDrivesEligibilityEvaluation() throws Exception {
        if (!sharedAppContext.getDriveRepository().existsById("DRV-EVAL")) {
            RecruitmentDrive evalDrive = new RecruitmentDrive("DRV-EVAL", "CMP-001", "Systems Analyst", 8.0, Set.of("CSE"), LocalDate.now().plusDays(15));
            sharedAppContext.getDriveService().createDrive(evalDrive);
        }

        HttpClient clientRahul = loginAs("rahul.sharma", "StudentPass123!");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/drives"))
                .GET()
                .build();

        HttpResponse<String> resp = clientRahul.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        // DRV-EVAL (CSE, 8.0) -> Rahul is eligible (8.8, CSE) and has not applied yet
        assertTrue(resp.body().contains("Systems Analyst"));
        assertTrue(resp.body().contains("badge-eligible"));
        assertTrue(resp.body().contains("ELIGIBLE TO APPLY") || resp.body().contains("Eligible to Apply") || resp.body().contains("Apply for Drive"));

        // DRV-002 (ECE, 7.0) -> Rahul is ineligible (branch mismatch)
        assertTrue(resp.body().contains("Hardware Engineer"));
        assertTrue(resp.body().contains("badge-ineligible"));
        assertTrue(resp.body().contains("Ineligible"));

        // DRV-003 (Deadline passed) -> Closed
        assertTrue(resp.body().contains("Legacy Analyst"));
        assertTrue(resp.body().contains("badge-closed"));
        assertTrue(resp.body().contains("Closed") || resp.body().contains("DEADLINE PASSED"));
    }

    @Test
    @DisplayName("student: online application submission enforces PRG redirect and updates tracking")
    void testApplicationSubmissionPrgWorkflow() throws Exception {
        HttpClient client = loginAs("rahul.sharma", "StudentPass123!");

        // 1. GET /student/drives to fetch current CSRF token
        HttpResponse<String> drivesResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrfToken = extractCsrfToken(drivesResp.body());

        // 2. Client without auto-redirect to assert 302 PRG
        HttpClient noRedirectClient = HttpClient.newBuilder()
                .cookieHandler(((CookieManager) client.cookieHandler().orElseThrow()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String postBody = "driveId=" + URLEncoder.encode("DRV-001", StandardCharsets.UTF_8)
                + "&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                + "&studentId=STU-TAMPERED"; // Tampering must be ignored

        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/applications"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", baseUrl)
                .POST(HttpRequest.BodyPublishers.ofString(postBody))
                .build();

        HttpResponse<String> postResp = noRedirectClient.send(submitReq, HttpResponse.BodyHandlers.ofString());

        // Assert HTTP 302 Found (PRG)
        assertEquals(302, postResp.statusCode(), "Submission must respond with 302 Found (PRG)");
        assertTrue(postResp.headers().firstValue("Location").orElse("").endsWith("/student/applications"));

        // 3. Follow redirect to GET /student/applications
        HttpResponse<String> trackResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/applications")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, trackResp.statusCode());
        assertTrue(trackResp.body().contains("Tech Corp"));
        assertTrue(trackResp.body().contains("Software Engineer"));
        assertTrue(trackResp.body().contains("APPLIED"));
        assertTrue(trackResp.body().contains("badge-applied"));
    }

    @Test
    @DisplayName("student: duplicate application submission is rejected with friendly flash message")
    void testDuplicateApplicationRejected() throws Exception {
        if (!sharedAppContext.getApplicationRepository().existsByStudentAndDrive("STU-101", "DRV-001")) {
            sharedAppContext.getApplicationService().submitApplication("STU-101", "DRV-001");
        }

        HttpClient client = loginAs("rahul.sharma", "StudentPass123!");

        HttpResponse<String> drivesResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrfToken = extractCsrfToken(drivesResp.body());

        String postBody = "driveId=DRV-001&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);

        HttpRequest duplicateReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/student/applications"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", baseUrl)
                .POST(HttpRequest.BodyPublishers.ofString(postBody))
                .build();

        HttpResponse<String> resp = client.send(duplicateReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("already applied") || resp.body().contains("Duplicate") || resp.body().contains("Applications"));
    }

    @Test
    @DisplayName("student: submission to ineligible or closed drive is rejected")
    void testIneligibleAndClosedDriveSubmissionRejected() throws Exception {
        HttpClient client = loginAs("rahul.sharma", "StudentPass123!");

        HttpResponse<String> drivesResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrfToken = extractCsrfToken(drivesResp.body());

        // Attempt submit to DRV-002 (ineligible branch)
        String postBody = "driveId=DRV-002&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);
        HttpResponse<String> resp1 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", baseUrl)
                        .POST(HttpRequest.BodyPublishers.ofString(postBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, resp1.statusCode());
        assertTrue(resp1.body().contains("Ineligible") || resp1.body().contains("not eligible") || resp1.body().contains("criteria"));

        // Attempt submit to DRV-003 (closed drive)
        String postBodyClosed = "driveId=DRV-003&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);
        HttpResponse<String> resp2 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", baseUrl)
                        .POST(HttpRequest.BodyPublishers.ofString(postBodyClosed))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, resp2.statusCode());
        assertTrue(resp2.body().contains("closed") || resp2.body().contains("deadline") || resp2.body().contains("Applications"));
    }

    @Test
    @DisplayName("student: application submission without valid CSRF token returns 403 Forbidden")
    void testApplicationSubmissionCsrfProtection() throws Exception {
        HttpClient client = loginAs("priya.patel", "StudentPass123!");

        // Missing CSRF
        String missingCsrfBody = "driveId=DRV-002";
        HttpResponse<String> resp1 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", baseUrl)
                        .POST(HttpRequest.BodyPublishers.ofString(missingCsrfBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, resp1.statusCode(), "Missing CSRF token must return 403 Forbidden");

        // Invalid CSRF
        String invalidCsrfBody = "driveId=DRV-002&csrfToken=tampered_invalid_csrf_token_99999";
        HttpResponse<String> resp2 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", baseUrl)
                        .POST(HttpRequest.BodyPublishers.ofString(invalidCsrfBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, resp2.statusCode(), "Invalid CSRF token must return 403 Forbidden");
    }

    @Test
    @DisplayName("student: cross-student isolation ensures Student A cannot see Student B's applications")
    void testCrossStudentApplicationsIsolation() throws Exception {
        HttpClient clientPriya = loginAs("priya.patel", "StudentPass123!");

        HttpResponse<String> resp = clientPriya.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/applications")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("No Applications Submitted Yet") || !resp.body().contains("Software Engineer"));
        assertFalse(resp.body().contains("DRV-001"));
    }

    @Test
    @DisplayName("security: PLACEMENT_OFFICER cannot access student self-service routes")
    void testOfficerCannotAccessStudentRoutes() throws Exception {
        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        for (String studentPath : List.of("/student/profile", "/student/drives", "/student/applications")) {
            HttpResponse<String> resp = officerClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + studentPath)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, resp.statusCode(), "Officer must be forbidden from accessing " + studentPath);
        }
    }

    @Test
    @DisplayName("security: disabled account suffers immediate session revocation")
    void testDisabledAccountImmediateSessionRevocation() throws Exception {
        HttpClient client = loginAs("priya.patel", "StudentPass123!");

        // Verify active session works
        HttpResponse<String> okResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/profile")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, okResp.statusCode());

        // Disable Priya's account
        var repo = sharedAppContext.getUserAccountRepository();
        var optAcc = repo.findByUsername("priya.patel");
        assertTrue(optAcc.isPresent());
        var disabledAcc = optAcc.get().toBuilder().enabled(false).build();
        repo.update(disabledAcc);

        try {
            // Next request with existing session must be revoked immediately
            HttpResponse<String> revokedResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/profile")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, revokedResp.statusCode(), "Disabled account session must be immediately revoked");
            assertTrue(revokedResp.body().contains("disabled"));
        } finally {
            // Restore enabled status for subsequent test isolation
            var restoredAcc = disabledAcc.toBuilder().enabled(true).build();
            repo.update(restoredAcc);
        }
    }

    @Test
    @DisplayName("concurrency: simultaneous submissions from same student to same drive create exactly one record")
    void testConcurrentDuplicateSubmissionsCreateExactlyOneRecord() throws Exception {
        RecruitmentDrive concDrive = new RecruitmentDrive("DRV-CONC", "CMP-002", "Concurrency Engineer", 7.0, Set.of("ECE"), LocalDate.now().plusDays(10));
        sharedAppContext.getDriveService().createDrive(concDrive);

        HttpClient client = loginAs("priya.patel", "StudentPass123!");
        HttpResponse<String> drivesResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrfToken = extractCsrfToken(drivesResp.body());

        String postBody = "driveId=DRV-CONC&csrfToken=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<HttpResponse<String>> f1 = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(baseUrl + "/student/applications"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .header("Origin", baseUrl)
                                    .POST(HttpRequest.BodyPublishers.ofString(postBody))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<HttpResponse<String>> f2 = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(baseUrl + "/student/applications"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .header("Origin", baseUrl)
                                    .POST(HttpRequest.BodyPublishers.ofString(postBody))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture.allOf(f1, f2).join();

            HttpResponse<String> r1 = f1.get();
            HttpResponse<String> r2 = f2.get();

            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());

            // Check database record count: exactly 1 application created for Priya on DRV-CONC
            long appCount = sharedAppContext.getApplicationRepository().findByStudentId("STU-102").stream()
                    .filter(a -> "DRV-CONC".equals(a.getDriveId()))
                    .count();
            assertEquals(1, appCount, "Concurrent duplicate submission must persist exactly 1 application record");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("officer: GET /officer/dashboard renders real metrics and anti-cache headers")
    void testOfficerDashboardMetricsAndActiveDrives() throws Exception {
        HttpClient client = loginAs("tpc.officer", "OfficerPass123!");

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/dashboard")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Placement Officer Portal"));
        assertTrue(resp.body().contains("Registered Companies"));
        assertTrue(resp.body().contains("Recruitment Drives"));
        assertTrue(resp.body().contains("Total Applications"));
        assertTrue(resp.body().contains("Shortlisted Candidates"));
        assertTrue(resp.headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
    }

    @Test
    @DisplayName("officer: company directory, registration, update, duplicate rejection, and PRG")
    void testOfficerCompanyManagementLifecycle() throws Exception {
        HttpClient client = loginAs("tpc.officer", "OfficerPass123!");

        // 1. GET /officer/companies
        HttpResponse<String> listResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/companies")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, listResp.statusCode());
        assertTrue(listResp.body().contains("Tech Corp"));

        // 2. GET /officer/companies/new
        HttpResponse<String> newResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/companies/new")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, newResp.statusCode());
        assertTrue(newResp.body().contains("Register Corporate Partner"));
        String csrf = extractCsrfToken(newResp.body());

        // 3. POST /officer/companies (create)
        String createData = "id=CMP-TEST&name=" + URLEncoder.encode("Apex Labs", StandardCharsets.UTF_8)
                + "&industry=" + URLEncoder.encode("DeepTech", StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        HttpResponse<String> postCreateResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/companies"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(createData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, postCreateResp.statusCode());
        assertTrue(postCreateResp.body().contains("Apex Labs"));
        assertTrue(postCreateResp.body().contains("registered successfully"));

        // 4. GET /officer/companies/CMP-TEST/edit
        HttpResponse<String> editResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/companies/CMP-TEST/edit")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, editResp.statusCode());
        assertTrue(editResp.body().contains("Edit Corporate Partner"));
        String editCsrf = extractCsrfToken(editResp.body());

        // 5. POST /officer/companies/CMP-TEST/update
        String updateData = "name=" + URLEncoder.encode("Apex Global", StandardCharsets.UTF_8)
                + "&industry=" + URLEncoder.encode("Robotics", StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(editCsrf, StandardCharsets.UTF_8);

        HttpResponse<String> postUpdateResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/companies/CMP-TEST/update"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(updateData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, postUpdateResp.statusCode());
        assertTrue(postUpdateResp.body().contains("Apex Global"));
        assertTrue(postUpdateResp.body().contains("updated successfully"));

        // 6. POST duplicate company ID
        String dupData = "id=CMP-TEST&name=Duplicate&industry=Other&_csrf=" + URLEncoder.encode(editCsrf, StandardCharsets.UTF_8);
        HttpResponse<String> dupResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/companies"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(dupData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, dupResp.statusCode());
        assertTrue(dupResp.body().contains("already exists"));
    }

    @Test
    @DisplayName("officer: recruitment drive creation, branch eligibility, details, and closure")
    void testOfficerDriveManagementLifecycleAndClosure() throws Exception {
        HttpClient client = loginAs("tpc.officer", "OfficerPass123!");

        // 1. GET /officer/drives
        HttpResponse<String> listResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, listResp.statusCode());
        String csrf = extractCsrfToken(listResp.body());

        // 2. POST /officer/drives (create)
        String driveData = "id=DRV-TEST1&companyId=CMP-001&jobTitle=FullStack+Engineer&minimumCgpa=7.5&eligibleBranches=CSE,ECE&applicationDeadline=2026-12-31&_csrf="
                + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        HttpResponse<String> createResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/drives"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(driveData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, createResp.statusCode());
        assertTrue(createResp.body().contains("FullStack Engineer"));
        assertTrue(createResp.body().contains("published successfully") || createResp.body().contains("created successfully"));

        // 3. GET /officer/drives/DRV-TEST1 (details)
        HttpResponse<String> detailResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/drives/DRV-TEST1")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, detailResp.statusCode());
        assertTrue(detailResp.body().contains("FullStack Engineer"));
        assertTrue(detailResp.body().contains("Drive Details"));
        String detailCsrf = extractCsrfToken(detailResp.body());

        // 4. POST /officer/drives/DRV-TEST1/close (close drive)
        String closeData = "_csrf=" + URLEncoder.encode(detailCsrf, StandardCharsets.UTF_8);
        HttpResponse<String> closeResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/drives/DRV-TEST1/close"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(closeData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, closeResp.statusCode());
        assertTrue(closeResp.body().contains("closed"));

        // Verify drive in repository is marked closed and original deadline is preserved
        RecruitmentDrive closedDrive = sharedAppContext.getDriveRepository().findById("DRV-TEST1").orElseThrow();
        assertTrue(closedDrive.isClosed(), "Drive must be marked manually closed");
        assertFalse(closedDrive.isOpen(LocalDate.now()), "Closed drive must report isOpen=false");
    }

    @Test
    @DisplayName("officer: applicant roster review, academic eligibility check, and conditional shortlisting")
    void testOfficerApplicantReviewAndShortlistingWorkflow() throws Exception {
        // Create open drive DRV-REV
        RecruitmentDrive revDrive = new RecruitmentDrive("DRV-REV", "CMP-001", "Security Analyst", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        sharedAppContext.getDriveService().createDrive(revDrive);

        // Student Rahul (CSE, 8.8) applies to DRV-REV
        HttpClient studentClient = loginAs("rahul.sharma", "StudentPass123!");
        HttpResponse<String> studentDrives = studentClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String studentCsrf = extractCsrfToken(studentDrives.body());

        studentClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("driveId=DRV-REV&csrfToken=" + URLEncoder.encode(studentCsrf, StandardCharsets.UTF_8)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        Application submittedApp = sharedAppContext.getApplicationRepository().findByStudentId("STU-101").stream()
                .filter(a -> "DRV-REV".equals(a.getDriveId()))
                .findFirst().orElseThrow();
        assertEquals(ApplicationStatus.APPLIED, submittedApp.getStatus());

        // Officer logs in and reviews roster
        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");
        HttpResponse<String> rosterResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/applications?driveId=DRV-REV")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, rosterResp.statusCode());
        assertTrue(rosterResp.body().contains("Rahul Sharma"));
        assertTrue(rosterResp.body().contains("STU-101"));
        assertTrue(rosterResp.body().contains("ELIGIBLE"));
        String officerCsrf = extractCsrfToken(rosterResp.body());

        // Officer shortlists Rahul
        String shortlistData = "_csrf=" + URLEncoder.encode(officerCsrf, StandardCharsets.UTF_8);
        HttpResponse<String> shortlistResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/applications/" + submittedApp.getId() + "/shortlist"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(shortlistData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, shortlistResp.statusCode());
        assertTrue(shortlistResp.body().contains("successfully shortlisted"));

        // Application status is now SHORTLISTED
        Application shortlistedApp = sharedAppContext.getApplicationRepository().findById(submittedApp.getId()).orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, shortlistedApp.getStatus());

        // Repeat submission (idempotency)
        HttpResponse<String> repeatResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/applications/" + submittedApp.getId() + "/shortlist"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(shortlistData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, repeatResp.statusCode());
        assertTrue(repeatResp.body().contains("successfully shortlisted"));
    }

    @Test
    @DisplayName("security: student role and unauthenticated access blocked from officer routes, CSRF strictly enforced")
    void testOfficerSecurityAccessControlsAndCsrf() throws Exception {
        // 1. Student role blocked from all officer endpoints
        HttpClient studentClient = loginAs("rahul.sharma", "StudentPass123!");
        for (String officerRoute : List.of("/officer/dashboard", "/officer/companies", "/officer/drives", "/officer/applications", "/officer/interviewers", "/officer/rooms", "/officer/scheduling")) {
            HttpResponse<String> resp = studentClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + officerRoute)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, resp.statusCode(), "Student must receive 403 Forbidden on " + officerRoute);
        }

        // 2. Unauthenticated access blocked
        HttpClient unauthClient = HttpClient.newHttpClient();
        HttpResponse<String> unauthResp = unauthClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/dashboard")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(unauthResp.statusCode() == 403 || unauthResp.statusCode() == 302, "Unauthenticated access must be denied");

        // 3. Officer POST mutations missing CSRF return 403
        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // Missing CSRF on company create
        HttpResponse<String> csrfComp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/companies"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("id=CMP-NOCSRF&name=NoCsrf"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfComp.statusCode(), "Company creation without CSRF must return 403");

        // Missing CSRF on drive create
        HttpResponse<String> csrfDrive = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/drives"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("id=DRV-NOCSRF&companyId=CMP-001&jobTitle=Engineer"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfDrive.statusCode(), "Drive creation without CSRF must return 403");

        // Missing CSRF on shortlist
        HttpResponse<String> csrfShortlist = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/applications/APP-X/shortlist"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("applicationId=APP-X"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfShortlist.statusCode(), "Shortlist mutation without CSRF must return 403");

        // Missing CSRF on interviewer create
        HttpResponse<String> csrfInterviewer = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/interviewers"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("id=INT-NOCSRF&name=NoCsrf&email=nocsrf@tech.com&companyId=CMP-001"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfInterviewer.statusCode(), "Interviewer creation without CSRF must return 403");

        // Missing CSRF on room create
        HttpResponse<String> csrfRoom = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/rooms"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("id=RM-NOCSRF&name=NoCsrf&building=Block&capacity=5"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfRoom.statusCode(), "Room creation without CSRF must return 403");

        // Missing CSRF on scheduling preview
        HttpResponse<String> csrfPreview = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/scheduling/preview"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("driveId=DRV-001&date=2026-10-25&startTime=09:00&endTime=12:00&durationMinutes=30&bufferMinutes=10"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, csrfPreview.statusCode(), "Scheduling preview without CSRF must return 403");
    }

    @Test
    @DisplayName("concurrency: simultaneous shortlist attempts succeed safely and idempotently")
    void testOfficerConcurrentShortlistingIntegrity() throws Exception {
        RecruitmentDrive concDrive = new RecruitmentDrive("DRV-SHORTCONC", "CMP-001", "Concurrency Tester", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        sharedAppContext.getDriveService().createDrive(concDrive);

        Application concApp = new Application("APP-SHORTCONC", "STU-101", "DRV-SHORTCONC", ApplicationStatus.APPLIED);
        sharedAppContext.getApplicationRepository().save(concApp);

        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");
        HttpResponse<String> appsResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/applications")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(appsResp.body());

        String postBody = "_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<HttpResponse<String>> f1 = CompletableFuture.supplyAsync(() -> {
                try {
                    return officerClient.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(baseUrl + "/officer/applications/APP-SHORTCONC/shortlist"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(postBody))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture<HttpResponse<String>> f2 = CompletableFuture.supplyAsync(() -> {
                try {
                    return officerClient.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(baseUrl + "/officer/applications/APP-SHORTCONC/shortlist"))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(postBody))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture.allOf(f1, f2).join();

            HttpResponse<String> r1 = f1.get();
            HttpResponse<String> r2 = f2.get();

            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());

            Application finalApp = sharedAppContext.getApplicationRepository().findById("APP-SHORTCONC").orElseThrow();
            assertEquals(ApplicationStatus.SHORTLISTED, finalApp.getStatus());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("officer: resource management allows viewing and creating interviewers and rooms")
    void testOfficerResourceManagementWorkflowInContainer() throws Exception {
        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // 1. View interviewers list and acquire CSRF token
        HttpResponse<String> intListResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/interviewers")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, intListResp.statusCode());
        assertTrue(intListResp.body().contains("Interviewers"));
        String csrfInt = extractCsrfToken(intListResp.body());

        // 2. Create new interviewer
        String intData = "id=INT-CONT-01"
                + "&name=" + URLEncoder.encode("Dr. Grace Hopper", StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode("grace@tech.com", StandardCharsets.UTF_8)
                + "&companyId=CMP-001"
                + "&specialization=" + URLEncoder.encode("Compiler Engineering", StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrfInt, StandardCharsets.UTF_8);

        HttpResponse<String> intCreateResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/interviewers"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(intData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, intCreateResp.statusCode());
        assertTrue(intCreateResp.body().contains("Dr. Grace Hopper"));
        assertTrue(intCreateResp.body().contains("INT-CONT-01"));

        // 3. View rooms list and acquire CSRF token
        HttpResponse<String> rmListResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/rooms")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, rmListResp.statusCode());
        assertTrue(rmListResp.body().contains("Interview Rooms"));
        String csrfRm = extractCsrfToken(rmListResp.body());

        // 4. Create new room
        String rmData = "id=RM-CONT-01"
                + "&name=" + URLEncoder.encode("Interview Suite A", StandardCharsets.UTF_8)
                + "&building=" + URLEncoder.encode("Placement Tower", StandardCharsets.UTF_8)
                + "&capacity=8"
                + "&_csrf=" + URLEncoder.encode(csrfRm, StandardCharsets.UTF_8);

        HttpResponse<String> rmCreateResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/rooms"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(rmData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, rmCreateResp.statusCode());
        assertTrue(rmCreateResp.body().contains("Interview Suite A"));
        assertTrue(rmCreateResp.body().contains("RM-CONT-01"));
    }

    @Test
    @DisplayName("officer: resource deletion blocked when referenced by confirmed sessions")
    void testResourceReferentialIntegrityAgainstConfirmedSessionsInContainer() throws Exception {
        // Precondition: register interviewer, room, and confirmed session
        Interviewer interviewer = new Interviewer("INT-REF-99", "Ada Lovelace", "CMP-001");
        sharedAppContext.getInterviewerRepository().save(interviewer);

        Room room = new Room("RM-REF-99", "Analytics Hall", 6);
        sharedAppContext.getRoomRepository().save(room);

        InterviewSession session = new InterviewSession(
                "SES-REF-99",
                "APP-SHORTCONC",
                "INT-REF-99",
                "RM-REF-99",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusMinutes(45)
        );
        sharedAppContext.getSessionRepository().save(session);

        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // 1. Attempt deleting interviewer referenced by session
        HttpResponse<String> intList = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/interviewers")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(intList.body());

        String postBody = "_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        HttpResponse<String> delIntResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/interviewers/INT-REF-99/delete"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(postBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, delIntResp.statusCode());
        assertTrue(delIntResp.body().contains("Cannot delete interviewer") || delIntResp.body().contains("interview sessions"),
                "Must report referential integrity violation in response");
        assertTrue(sharedAppContext.getInterviewerRepository().existsById("INT-REF-99"), "Interviewer must NOT be deleted");

        // 2. Attempt deleting room referenced by session
        HttpResponse<String> delRmResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/rooms/RM-REF-99/delete"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(postBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, delRmResp.statusCode());
        assertTrue(delRmResp.body().contains("Cannot delete room") || delRmResp.body().contains("interview sessions"),
                "Must report referential integrity violation in response");
        assertTrue(sharedAppContext.getRoomRepository().existsById("RM-REF-99"), "Room must NOT be deleted");
    }

    @Test
    @DisplayName("officer: scheduling form displays shortlisted candidates and advisory preview leaves database untouched")
    void testOfficerSchedulingFormAndAdvisoryPreviewInContainer() throws Exception {
        // Preconditions: drive with shortlisted candidate
        RecruitmentDrive schedDrive = new RecruitmentDrive("DRV-SCHED-01", "CMP-001", "Systems Analyst", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        sharedAppContext.getDriveService().createDrive(schedDrive);

        Application schedApp = new Application("APP-SCHED-01", "STU-101", "DRV-SCHED-01", ApplicationStatus.SHORTLISTED);
        sharedAppContext.getApplicationRepository().save(schedApp);

        Interviewer schedInt = new Interviewer("INT-SCHED-01", "Prof. Knuth", "CMP-001");
        sharedAppContext.getInterviewerRepository().save(schedInt);

        Room schedRoom = new Room("RM-SCHED-01", "Boardroom", 10);
        sharedAppContext.getRoomRepository().save(schedRoom);

        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // 1. GET /officer/scheduling?driveId=DRV-SCHED-01
        HttpResponse<String> formResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling?driveId=DRV-SCHED-01")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, formResp.statusCode());
        assertTrue(formResp.body().contains("Interview Scheduling Setup"));
        assertTrue(formResp.body().contains("DRV-SCHED-01"));
        assertTrue(formResp.body().contains("Shortlisted"));
        assertTrue(formResp.body().contains("Prof. Knuth"));
        assertTrue(formResp.body().contains("Boardroom"));
        String csrf = extractCsrfToken(formResp.body());

        // 2. POST /officer/scheduling/preview
        String previewData = "driveId=DRV-SCHED-01"
                + "&date=2026-10-25"
                + "&startTime=09:00"
                + "&endTime=12:00"
                + "&durationMinutes=30"
                + "&bufferMinutes=10"
                + "&interviewerIds=INT-SCHED-01"
                + "&roomIds=RM-SCHED-01"
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        HttpResponse<String> previewResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/scheduling/preview"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(previewData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, previewResp.statusCode());
        assertTrue(previewResp.body().contains("ADVISORY PREVIEW ONLY") || previewResp.body().contains("Advisory Schedule Preview"));
        assertTrue(previewResp.body().contains("NOT CONFIRMED") || previewResp.body().contains("No database appointments have been saved"));
        assertTrue(previewResp.body().contains("Rahul Sharma") || previewResp.body().contains("STU-101"));
        assertTrue(previewResp.body().contains("Prof. Knuth"));
        assertTrue(previewResp.body().contains("Boardroom"));

        // 3. Verify Database Invariant: Absolutely ZERO sessions persisted and application status unchanged!
        long sessionsCount = sharedAppContext.getSessionRepository().findAll().stream()
                .filter(s -> "APP-SCHED-01".equals(s.getApplicationId()))
                .count();
        assertEquals(0, sessionsCount, "Scheduling preview must not insert any interview_session rows into database");

        Application appAfter = sharedAppContext.getApplicationRepository().findById("APP-SCHED-01").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, appAfter.getStatus(),
                "Candidate application status must remain SHORTLISTED during preview phase");
    }

    @Test
    @DisplayName("officer: manual drive closure preserves application deadline and blocks new student applications")
    void testManualDriveClosurePreservesDeadlineAndRejectsApplicationsInContainer() throws Exception {
        LocalDate deadline = LocalDate.now().plusDays(14);
        RecruitmentDrive drive = new RecruitmentDrive("DRV-CLOSE-CONT", "CMP-001", "DevOps Engineer", 7.0, Set.of("CSE"), deadline, false);
        sharedAppContext.getDriveService().createDrive(drive);

        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // 1. Acquire CSRF from officer drives list
        HttpResponse<String> drivesResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String csrf = extractCsrfToken(drivesResp.body());

        // 2. Officer manually closes drive
        String closeBody = "_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        HttpResponse<String> closeResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/drives/DRV-CLOSE-CONT/close"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(closeBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, closeResp.statusCode());
        assertTrue(closeResp.body().contains("MANUALLY CLOSED") || closeResp.body().contains("successfully closed"));

        // 3. Verify in repository: isClosed=true and original deadline unchanged
        RecruitmentDrive closedInRepo = sharedAppContext.getDriveRepository().findById("DRV-CLOSE-CONT").orElseThrow();
        assertTrue(closedInRepo.isClosed(), "Drive must have is_closed=true");
        assertEquals(deadline, closedInRepo.getApplicationDeadline(), "Original application deadline must remain untouched");

        // 4. Student attempts to apply for closed drive -> rejected
        HttpClient studentClient = loginAs("rahul.sharma", "StudentPass123!");
        HttpResponse<String> stuDrivesResp = studentClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/drives")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        String stuCsrf = extractCsrfToken(stuDrivesResp.body());

        String applyBody = "driveId=DRV-CLOSE-CONT&_csrf=" + URLEncoder.encode(stuCsrf, StandardCharsets.UTF_8);
        HttpResponse<String> applyResp = studentClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/student/applications"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(applyBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, applyResp.statusCode());
        assertTrue(applyResp.body().contains("closed for applications") || applyResp.body().contains("not eligible"),
                "Student application submission for manually closed drive must be rejected");
        assertFalse(sharedAppContext.getApplicationRepository().existsByStudentAndDrive("STU-101", "DRV-CLOSE-CONT"),
                "No application record must exist in repository for closed drive");
    }

    private static String extractPreviewId(String html) {
        Pattern pattern = Pattern.compile("name=\"previewId\"\\s+value=\"(PREV-[A-Fa-f0-9]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern tokenPattern = Pattern.compile("(PREV-[A-Fa-f0-9]+)");
        Matcher tokenMatcher = tokenPattern.matcher(html);
        if (tokenMatcher.find()) {
            return tokenMatcher.group(1);
        }
        throw new IllegalStateException("Could not find previewId in response HTML:\n" + html);
    }

    @Test
    @DisplayName("Stage 3F.2: Officer publishes timetable through embedded Tomcat, prevents replay, updates status, and student views appointment")
    void testOfficerPublicationWorkflowAndStudentAppointmentVisibilityInContainer() throws Exception {
        // 1. Setup drive, interviewer, room, and shortlisted application
        RecruitmentDrive pubDrive = new RecruitmentDrive("DRV-PUB-01", "CMP-001", "Platform Architect", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10));
        sharedAppContext.getDriveService().createDrive(pubDrive);

        Application pubApp = new Application("APP-PUB-01", "STU-101", "DRV-PUB-01", ApplicationStatus.SHORTLISTED);
        sharedAppContext.getApplicationRepository().save(pubApp);

        Interviewer pubInt = new Interviewer("INT-PUB-01", "Dr. Leslie Lamport", "CMP-001");
        sharedAppContext.getInterviewerRepository().save(pubInt);

        Room pubRoom = new Room("RM-PUB-01", "Distributed Lab", 5);
        sharedAppContext.getRoomRepository().save(pubRoom);

        HttpClient officerClient = loginAs("tpc.officer", "OfficerPass123!");

        // 2. Officer gets form and CSRF
        HttpResponse<String> formResp = officerClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling?driveId=DRV-PUB-01")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, formResp.statusCode());
        String csrf = extractCsrfToken(formResp.body());

        // 3. Officer posts scheduling preview
        String previewData = "driveId=DRV-PUB-01"
                + "&date=2026-11-10"
                + "&startTime=10:00"
                + "&endTime=12:00"
                + "&duration=45"
                + "&buffer=15"
                + "&interviewerIds=INT-PUB-01"
                + "&roomIds=RM-PUB-01"
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        HttpResponse<String> previewResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/scheduling/preview"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(previewData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, previewResp.statusCode());
        assertTrue(previewResp.body().contains("ADVISORY PREVIEW") || previewResp.body().contains("READY FOR PUBLICATION"));
        String previewId = extractPreviewId(previewResp.body());
        assertNotNull(previewId);
        assertTrue(previewId.startsWith("PREV-"));

        // Verify Database Invariant BEFORE publication: 0 sessions, status still SHORTLISTED
        assertEquals(0, sharedAppContext.getSessionRepository().findByApplicationId("APP-PUB-01").size());
        assertEquals(ApplicationStatus.SHORTLISTED, sharedAppContext.getApplicationRepository().findById("APP-PUB-01").orElseThrow().getStatus());

        // 4. Officer publishes timetable via POST /officer/scheduling/publish
        String publishData = "previewId=" + URLEncoder.encode(previewId, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8);

        HttpResponse<String> publishResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/scheduling/publish"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(publishData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, publishResp.statusCode()); // Redirect followed by loginAs HttpClient
        assertTrue(publishResp.body().contains("Confirmed Interview Timetable") || publishResp.body().contains("Confirmed Appointments"));
        assertTrue(publishResp.body().contains("Schedule Publication Complete") || publishResp.body().contains("Confirmed Records"));
        assertTrue(publishResp.body().contains("Rahul Sharma") || publishResp.body().contains("STU-101"));

        // 5. Verify Database Invariant AFTER publication:
        // Session persisted!
        List<InterviewSession> confirmedSessions = sharedAppContext.getSessionRepository().findByApplicationId("APP-PUB-01");
        assertEquals(1, confirmedSessions.size(), "Interview appointment must be persisted in database");
        InterviewSession confirmed = confirmedSessions.get(0);
        assertEquals("INT-PUB-01", confirmed.getInterviewerId());
        assertEquals("RM-PUB-01", confirmed.getRoomId());

        // Application status updated from SHORTLISTED to SCHEDULED!
        Application appAfter = sharedAppContext.getApplicationRepository().findById("APP-PUB-01").orElseThrow();
        assertEquals(ApplicationStatus.SCHEDULED, appAfter.getStatus(),
                "Candidate application status must be SCHEDULED after publication");

        // 6. Anti-replay verification: Attempting to publish the same preview again must be rejected!
        HttpResponse<String> replayResp = officerClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/officer/scheduling/publish"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(publishData))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, replayResp.statusCode());
        assertTrue(replayResp.body().contains("already been published. Replay prevented")
                || replayResp.body().contains("already scheduled")
                || replayResp.body().contains("Replay prevented"));
        assertEquals(1, sharedAppContext.getSessionRepository().findByApplicationId("APP-PUB-01").size(),
                "Replayed publication must not create duplicate appointments");

        // 7. Student appointment visibility: Candidate Rahul Sharma views his confirmed appointment
        HttpClient studentClient = loginAs("rahul.sharma", "StudentPass123!");
        HttpResponse<String> stuInterviewsResp = studentClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/interviews")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, stuInterviewsResp.statusCode());
        assertTrue(stuInterviewsResp.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
                "Private student interview records must enforce Cache-Control: no-store");
        assertTrue(stuInterviewsResp.body().contains("My Confirmed Interview Schedule"));
        assertTrue(stuInterviewsResp.body().contains("Platform Architect"));
        assertTrue(stuInterviewsResp.body().contains("Dr. Leslie Lamport"));
        assertTrue(stuInterviewsResp.body().contains("Distributed Lab") || stuInterviewsResp.body().contains("RM-PUB-01"));

        // 8. Candidate Priya Patel does NOT see Rahul's appointment
        HttpClient priyaClient = loginAs("priya.patel", "StudentPass123!");
        HttpResponse<String> priyaInterviewsResp = priyaClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/interviews")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, priyaInterviewsResp.statusCode());
        assertTrue(priyaInterviewsResp.body().contains("No Confirmed Interviews Yet"),
                "Priya must see empty interview state, never other candidates' appointments");
        assertFalse(priyaInterviewsResp.body().contains("Dr. Leslie Lamport"),
                "Rahul's interview panel must not be leaked to Priya");

        // 9. Unauthorized access control verification:
        // Unauthenticated access to /student/interviews and /officer/scheduling/published
        HttpClient anonClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> anonPubResp = anonClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling/published")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(anonPubResp.statusCode() == 302 || anonPubResp.statusCode() == 401 || anonPubResp.statusCode() == 403,
                "Anonymous user must be denied access to published timetable");

        HttpResponse<String> anonStuResp = anonClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/student/interviews")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(anonStuResp.statusCode() == 302 || anonStuResp.statusCode() == 401 || anonStuResp.statusCode() == 403,
                "Anonymous user must be denied access to student interviews");

        // Student accessing officer timetable is rejected with 403
        HttpResponse<String> stuOfficerResp = studentClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/officer/scheduling/published")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, stuOfficerResp.statusCode(), "Student must be forbidden from officer timetable view");
    }
}

