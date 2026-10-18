package com.hireflow.api.student;

import com.jayway.jsonpath.JsonPath;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
import com.hireflow.web.security.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Real HTTP Integration Test verifying:
 * - Part 14: Full multi-student automated recruitment workflow (Student A & Student B).
 * - Real HTTP session rotation upon student login (SID_A -> SID_B).
 * - Profile inspection, drives catalog browsing, and drive eligibility check over HTTP.
 * - Application submission with CSRF protection and HTTP 201 Created.
 * - Anti-caching response headers (Cache-Control: no-store).
 * - Duplicate submission prevention (HTTP 409 Conflict).
 * - Cross-student application isolation (Student B receives 404 for Student A's application ID).
 * - Part 10: Concurrent race condition testing with real PostgreSQL unique constraint enforcement.
 * - Two different students applying concurrently to the same recruitment drive.
 * - Direct database persistence verification in PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentWorkflowRealHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private HttpClient httpClient;

    private static final String STU_A_ID = "STU-E2E-A01";
    private static final String STU_B_ID = "STU-E2E-B02";
    private static final String CMP_ID = "CMP-E2E-001";
    private static final String DRV_ID = "DRV-E2E-001";
    private static final String PASSWORD = "E2eStudentSecurePass2026!";

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        cleanDatabase();

        // 1. Create Student records
        Student studentA = new Student(STU_A_ID, "Alice Armstrong", "alice.armstrong@hireflow.test", "CSE", 8.80, 2026, true);
        studentRepository.add(studentA);

        Student studentB = new Student(STU_B_ID, "Brian Bennett", "brian.bennett@hireflow.test", "CSE", 8.20, 2026, true);
        studentRepository.add(studentB);

        // 2. Create Company
        Company company = new Company(CMP_ID, "Global Tech Systems", "Enterprise Solutions");
        companyRepository.add(company);

        // 3. Create Open Recruitment Drive (CSE, min 7.50, future deadline)
        RecruitmentDrive drive = new RecruitmentDrive(
                DRV_ID, CMP_ID, "Software Engineer - Backend", 7.50, Set.of("CSE"), LocalDate.now().plusMonths(2), false
        );
        driveRepository.add(drive);

        // 4. Create User Accounts
        String hash = passwordHasher.hashPassword(PASSWORD.toCharArray());

        UserAccount userA = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student_a_e2e")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_A_ID)
                .displayName("Alice Armstrong")
                .enabled(true)
                .build();
        userAccountRepository.save(userA);

        UserAccount userB = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student_b_e2e")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_B_ID)
                .displayName("Brian Bennett")
                .enabled(true)
                .build();
        userAccountRepository.save(userB);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        try {
            applicationRepository.findByStudentId(STU_A_ID).forEach(a -> applicationRepository.deleteById(a.getId()));
            applicationRepository.findByStudentId(STU_B_ID).forEach(a -> applicationRepository.deleteById(a.getId()));

            driveRepository.deleteById(DRV_ID);
            companyRepository.deleteById(CMP_ID);

            userAccountRepository.findByUsername("student_a_e2e").ifPresent(a -> userAccountRepository.deleteById(a.getId()));
            userAccountRepository.findByUsername("student_b_e2e").ifPresent(a -> userAccountRepository.deleteById(a.getId()));

            studentRepository.deleteById(STU_A_ID);
            studentRepository.deleteById(STU_B_ID);
        } catch (Exception ignored) {
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private String extractSessionId(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String cookie : cookies) {
            if (cookie.contains("JSESSIONID=")) {
                int start = cookie.indexOf("JSESSIONID=") + "JSESSIONID=".length();
                int end = cookie.indexOf(';', start);
                return (end > 0) ? cookie.substring(start, end) : cookie.substring(start);
            }
        }
        return null;
    }

    private record ClientSession(String sessionId, String csrfToken, String csrfHeader) {}

    private ClientSession login(String username, String password) throws Exception {
        // 1. Obtain CSRF
        HttpResponse<String> csrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrfRes.statusCode());

        String initialSid = extractSessionId(csrfRes);
        assertNotNull(initialSid, "Initial session ID must be generated");
        String csrfToken = JsonPath.read(csrfRes.body(), "$.token");
        String csrfHeader = JsonPath.read(csrfRes.body(), "$.headerName");

        // 2. Login
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> loginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + initialSid)
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, loginRes.statusCode());
        String rotatedSid = extractSessionId(loginRes);
        assertNotNull(rotatedSid, "Rotated session ID must be returned on login");
        assertNotEquals(initialSid, rotatedSid, "Session ID must rotate upon authentication");

        // 3. Obtain post-auth CSRF token for the rotated session
        HttpResponse<String> postAuthCsrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .header("Cookie", "JSESSIONID=" + rotatedSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        String postAuthCsrf = JsonPath.read(postAuthCsrfRes.body(), "$.token");

        return new ClientSession(rotatedSid, postAuthCsrf, csrfHeader);
    }

    @Test
    @DisplayName("Stage 4C Primary: Part 14 Full End-to-End Multi-Student Recruitment Workflow")
    void testPart14EndToEndStudentWorkflow() throws Exception {
        // =====================================================================
        // Student A: Login, inspect profile, browse drives, verify eligibility
        // =====================================================================
        ClientSession studentA = login("student_a_e2e", PASSWORD);

        // 1. Load own student profile
        HttpResponse<String> profileRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/profile"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, profileRes.statusCode());
        assertTrue(profileRes.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(STU_A_ID, JsonPath.read(profileRes.body(), "$.id"));
        assertEquals("Alice Armstrong", JsonPath.read(profileRes.body(), "$.name"));
        assertEquals("CSE", JsonPath.read(profileRes.body(), "$.branch"));

        // 2. Browse recruitment drives
        HttpResponse<String> drivesRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/drives"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, drivesRes.statusCode());
        assertTrue(drivesRes.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        List<?> drivesList = JsonPath.read(drivesRes.body(), "$");
        assertFalse(drivesList.isEmpty());

        // 3. Check drive eligibility
        HttpResponse<String> eligRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/drives/" + DRV_ID + "/eligibility"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, eligRes.statusCode());
        assertTrue((Boolean) JsonPath.read(eligRes.body(), "$.academicallyEligible"));
        assertTrue((Boolean) JsonPath.read(eligRes.body(), "$.canApply"));
        assertFalse((Boolean) JsonPath.read(eligRes.body(), "$.alreadyApplied"));

        // 4. Submit application with CSRF protection
        HttpResponse<String> submitRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .header(studentA.csrfHeader(), studentA.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, submitRes.statusCode(), "Application submission must return 201 Created");
        assertTrue(submitRes.headers().firstValue("Location").orElse("").contains("/api/v1/student/applications/"));
        String studentAAppId = JsonPath.read(submitRes.body(), "$.id");
        assertNotNull(studentAAppId);
        assertEquals(STU_A_ID, JsonPath.read(submitRes.body(), "$.studentId"));
        assertEquals(DRV_ID, JsonPath.read(submitRes.body(), "$.driveId"));
        assertEquals("APPLIED", JsonPath.read(submitRes.body(), "$.status"));

        // 5. Retrieve application list
        HttpResponse<String> listRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, listRes.statusCode());
        int totalElements = JsonPath.read(listRes.body(), "$.totalElements");
        assertEquals(1, totalElements);
        assertEquals(studentAAppId, JsonPath.read(listRes.body(), "$.content[0].id"));

        // 6. Retrieve single application detail
        HttpResponse<String> detailRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications/" + studentAAppId))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, detailRes.statusCode());
        assertEquals(studentAAppId, JsonPath.read(detailRes.body(), "$.id"));
        assertEquals(STU_A_ID, JsonPath.read(detailRes.body(), "$.studentId"));

        // 7. Retry submission: verify duplicate returns 409 Conflict
        HttpResponse<String> retryRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .header(studentA.csrfHeader(), studentA.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(409, retryRes.statusCode(), "Duplicate submission must return 409 Conflict");

        // 8. Student A Logout
        HttpResponse<String> logoutRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .header(studentA.csrfHeader(), studentA.csrfToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutRes.statusCode());

        // Subsequent access with Student A's session is rejected
        HttpResponse<String> meAfterLogout = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/profile"))
                .header("Cookie", "JSESSIONID=" + studentA.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, meAfterLogout.statusCode());

        // =====================================================================
        // Student B: Login, attempt cross-student access, submit own application
        // =====================================================================
        ClientSession studentB = login("student_b_e2e", PASSWORD);

        // 9. Student B attempts to access Student A's application by ID -> 404 NOT FOUND (resource not disclosed!)
        HttpResponse<String> crossAccessRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications/" + studentAAppId))
                .header("Cookie", "JSESSIONID=" + studentB.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(404, crossAccessRes.statusCode(),
                "CRITICAL: Cross-student application inspection MUST return 404 to avoid disclosing existence");

        // 10. Student B's own application list is empty initially
        HttpResponse<String> bListRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Cookie", "JSESSIONID=" + studentB.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bListRes.statusCode());
        assertEquals(0, (int) JsonPath.read(bListRes.body(), "$.totalElements"));

        // 11. Student B submits own application for the same drive
        HttpResponse<String> bSubmitRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + studentB.sessionId())
                .header(studentB.csrfHeader(), studentB.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(201, bSubmitRes.statusCode());
        String studentBAppId = JsonPath.read(bSubmitRes.body(), "$.id");
        assertNotNull(studentBAppId);
        assertNotEquals(studentAAppId, studentBAppId, "Different students must receive distinct application IDs");

        // 12. Direct PostgreSQL database persistence verification
        assertTrue(applicationRepository.existsById(studentAAppId), "Student A application must exist in PostgreSQL");
        assertTrue(applicationRepository.existsById(studentBAppId), "Student B application must exist in PostgreSQL");
        assertEquals(STU_A_ID, applicationRepository.findById(studentAAppId).get().getStudentId());
        assertEquals(STU_B_ID, applicationRepository.findById(studentBAppId).get().getStudentId());
    }

    @Test
    @DisplayName("Part 10 Concurrency: High-concurrency duplicate submission hits PostgreSQL unique constraint")
    void testConcurrentDuplicateSubmissions() throws Exception {
        ClientSession session = login("student_a_e2e", PASSWORD);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Integer> submitTask = () -> {
            startGate.await(); // Synchronize thread release
            HttpResponse<String> res = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", "JSESSIONID=" + session.sessionId())
                    .header(session.csrfHeader(), session.csrfToken())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            return res.statusCode();
        };

        Future<Integer> f1 = executor.submit(submitTask);
        Future<Integer> f2 = executor.submit(submitTask);
        startGate.countDown(); // Fire simultaneous requests

        int code1 = f1.get();
        int code2 = f2.get();
        executor.shutdown();

        // Exactly one must be 201 Created and the other must be 409 Conflict
        assertTrue((code1 == 201 && code2 == 409) || (code1 == 409 && code2 == 201),
                "Under race conditions, exactly one submission succeeds (201) and one conflicts (409). Codes: " + code1 + ", " + code2);

        // Verify PostgreSQL contains strictly ONE application record
        List<?> studentAApps = applicationRepository.findByStudentId(STU_A_ID);
        assertEquals(1, studentAApps.size(), "PostgreSQL must contain strictly 1 application record for Student A");
    }

    @Test
    @DisplayName("Part 10 Concurrency: Two different eligible students submitting concurrently both succeed")
    void testConcurrentSubmissionsDifferentStudents() throws Exception {
        ClientSession sessionA = login("student_a_e2e", PASSWORD);
        ClientSession sessionB = login("student_b_e2e", PASSWORD);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Integer> submitA = () -> {
            startGate.await();
            return httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", "JSESSIONID=" + sessionA.sessionId())
                    .header(sessionA.csrfHeader(), sessionA.csrfToken())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        };

        Callable<Integer> submitB = () -> {
            startGate.await();
            return httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                    .header("Content-Type", "application/json")
                    .header("Cookie", "JSESSIONID=" + sessionB.sessionId())
                    .header(sessionB.csrfHeader(), sessionB.csrfToken())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"driveId\":\"" + DRV_ID + "\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        };

        Future<Integer> fA = executor.submit(submitA);
        Future<Integer> fB = executor.submit(submitB);
        startGate.countDown();

        assertEquals(201, (int) fA.get(), "Student A concurrent submission must succeed with 201");
        assertEquals(201, (int) fB.get(), "Student B concurrent submission must succeed with 201");
        executor.shutdown();

        assertEquals(1, applicationRepository.findByStudentId(STU_A_ID).size());
        assertEquals(1, applicationRepository.findByStudentId(STU_B_ID).size());
    }
}
