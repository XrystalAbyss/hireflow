package com.hireflow.api.security;

import com.jayway.jsonpath.JsonPath;
import com.hireflow.model.Student;
import com.hireflow.repository.StudentRepository;
import com.hireflow.web.security.BruteForceThrottler;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Real HTTP Integration Test verifying:
 * - Session fixation closure (session rotation SID_A -> SID_B upon authentication).
 * - Rejection of obsolete pre-authentication session ID (SID_A returns 401).
 * - Correct authentication and profile retrieval with rotated session ID (SID_B).
 * - CSRF token lifecycle and protection.
 * - Logout session and cookie invalidation.
 * - Multi-user session isolation (STUDENT, PLACEMENT_OFFICER, ADMINISTRATOR).
 * - Immediate continuous account revocation (disablement and lockout).
 * - Role mutation defense against stale authorities.
 * - Cookie security attributes (HttpOnly, SameSite, Path, Max-Age).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionFixationAndRevocationRealHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private BruteForceThrottler bruteForceThrottler;

    private HttpClient httpClient;

    private static final String STUDENT_USER = "real_stu_4b1";
    private static final String STUDENT_ID = "STU-REAL-4B1";
    private static final String OFFICER_USER = "real_off_4b1";
    private static final String ADMIN_USER = "real_adm_4b1";
    private static final String TEST_PASSWORD = "StrongSecurePassword2026!";

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        cleanDatabase();

        // 1. Create Student domain record
        if (!studentRepository.existsById(STUDENT_ID)) {
            Student student = new Student(
                    STUDENT_ID,
                    "Real Test Student",
                    "real.student@hireflow.test",
                    "Information Science",
                    8.95,
                    2026
            );
            studentRepository.add(student);
        }

        String passwordHash = passwordHasher.hashPassword(TEST_PASSWORD.toCharArray());

        // 2. Persist Student UserAccount
        UserAccount studentAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(STUDENT_USER)
                .passwordHash(passwordHash)
                .role(UserRole.STUDENT)
                .studentId(STUDENT_ID)
                .displayName("Real Test Student")
                .enabled(true)
                .build();
        userAccountRepository.save(studentAccount);

        // 3. Persist Officer UserAccount
        UserAccount officerAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(OFFICER_USER)
                .passwordHash(passwordHash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Real Test Officer")
                .enabled(true)
                .build();
        userAccountRepository.save(officerAccount);

        // 4. Persist Administrator UserAccount
        UserAccount adminAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(ADMIN_USER)
                .passwordHash(passwordHash)
                .role(UserRole.ADMINISTRATOR)
                .displayName("Real Test Admin")
                .enabled(true)
                .build();
        userAccountRepository.save(adminAccount);

        bruteForceThrottler.clear();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        bruteForceThrottler.clear();
    }

    private void cleanDatabase() {
        try {
            userAccountRepository.findByUsername(STUDENT_USER).ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            userAccountRepository.findByUsername(OFFICER_USER).ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            userAccountRepository.findByUsername(ADMIN_USER).ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            studentRepository.deleteById(STUDENT_ID);
        } catch (Exception ignored) {
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    // Helper: Extract session ID from Set-Cookie headers
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

    // Helper: Find full Set-Cookie header for JSESSIONID
    private String findSessionCookieHeader(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String cookie : cookies) {
            if (cookie.contains("JSESSIONID=")) {
                return cookie;
            }
        }
        return null;
    }

    private record CsrfInfo(String token, String headerName, String sessionId) {}

    // Helper: Call GET /api/v1/auth/csrf and obtain CSRF info + initial SID_A
    private CsrfInfo obtainCsrf(String existingSessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET();

        if (existingSessionId != null) {
            builder.header("Cookie", "JSESSIONID=" + existingSessionId);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        String sid = extractSessionId(response);
        if (sid == null && existingSessionId != null) {
            sid = existingSessionId;
        }

        String token = JsonPath.read(response.body(), "$.token");
        String headerName = JsonPath.read(response.body(), "$.headerName");

        assertNotNull(sid, "Session ID must be established by CSRF endpoint");
        assertNotNull(token, "CSRF token must not be null");
        return new CsrfInfo(token, headerName, sid);
    }

    @Test
    @DisplayName("Stage 4B.1 Primary: Full Step A-F Session Rotation & Fixation Closure Test")
    void testFullSessionRotationStepAtoF() throws Exception {
        // =====================================================================
        // Step A — Obtain anonymous session SID_A and CSRF token
        // =====================================================================
        CsrfInfo stepA = obtainCsrf(null);
        String sidA = stepA.sessionId();
        String csrfA = stepA.token();
        String csrfHeader = stepA.headerName();

        assertNotNull(sidA, "Step A: Initial anonymous session SID_A must exist");
        assertFalse(sidA.isBlank());

        // Anonymous session is unauthenticated
        HttpRequest meAnonReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidA)
                .GET()
                .build();
        HttpResponse<String> meAnonRes = httpClient.send(meAnonReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, meAnonRes.statusCode(), "Step A: Anonymous session must return 401 on protected endpoint");

        // =====================================================================
        // Step B — Authenticate with SID_A + CSRF token + valid credentials
        // =====================================================================
        String loginPayload = "{\"username\":\"" + STUDENT_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}";

        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + sidA)
                .header(csrfHeader, csrfA)
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginRes = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode(), "Step B: Login must succeed with HTTP 200");

        String sidB = extractSessionId(loginRes);
        assertNotNull(sidB, "Step B: Post-authentication session cookie SID_B must be returned in Set-Cookie");

        // CRITICAL FIXATION ASSERTION: Session ID MUST rotate
        assertNotEquals(sidA, sidB, "CRITICAL: Session ID MUST rotate upon successful authentication (SID_B != SID_A)");

        // Assert response establishes identity without exposing credentials or session tokens
        String username = JsonPath.read(loginRes.body(), "$.username");
        String role = JsonPath.read(loginRes.body(), "$.role");
        String studentId = JsonPath.read(loginRes.body(), "$.studentId");
        assertEquals(STUDENT_USER, username);
        assertEquals("STUDENT", role);
        assertEquals(STUDENT_ID, studentId);
        assertFalse(loginRes.body().contains(sidA), "Session ID must never be leaked in response body");
        assertFalse(loginRes.body().contains(sidB), "Session ID must never be leaked in response body");

        // =====================================================================
        // Step C — Reuse old session SID_A: Must return 401 Unauthorized
        // =====================================================================
        HttpRequest meOldSidReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidA)
                .GET()
                .build();
        HttpResponse<String> meOldSidRes = httpClient.send(meOldSidReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, meOldSidRes.statusCode(),
                "Step C: Old pre-authentication session SID_A MUST NOT be authenticated (401 Unauthorized)");

        // =====================================================================
        // Step D — Use new rotated session SID_B: Must return 200 OK
        // =====================================================================
        HttpRequest meNewSidReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidB)
                .GET()
                .build();
        HttpResponse<String> meNewSidRes = httpClient.send(meNewSidReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meNewSidRes.statusCode(), "Step D: Rotated session SID_B must return HTTP 200 OK");

        assertEquals(STUDENT_USER, JsonPath.read(meNewSidRes.body(), "$.username"));
        assertEquals("STUDENT", JsonPath.read(meNewSidRes.body(), "$.role"));
        assertEquals(STUDENT_ID, JsonPath.read(meNewSidRes.body(), "$.studentId"));

        // =====================================================================
        // Step E — Logout with SID_B and valid CSRF token
        // =====================================================================
        // Fetch fresh CSRF token tied to SID_B
        CsrfInfo stepBcsrf = obtainCsrf(sidB);

        HttpRequest logoutReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("Cookie", "JSESSIONID=" + sidB)
                .header(stepBcsrf.headerName(), stepBcsrf.token())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> logoutRes = httpClient.send(logoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutRes.statusCode(), "Step E: Logout must succeed");

        // Verify Set-Cookie header contains Max-Age=0 or clearing cookie
        String logoutCookie = findSessionCookieHeader(logoutRes);
        assertNotNull(logoutCookie, "Logout must send a clearing Set-Cookie header");
        assertTrue(logoutCookie.contains("Max-Age=0") || logoutCookie.contains("Expires="),
                "Logout cookie must instruct client to expire session immediately");

        // Subsequent request using SID_B must be rejected
        HttpRequest meAfterLogoutReq = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidB)
                .GET()
                .build();
        HttpResponse<String> meAfterLogoutRes = httpClient.send(meAfterLogoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, meAfterLogoutRes.statusCode(),
                "Step E: Rotated session SID_B must be completely invalidated after logout (401 Unauthorized)");
    }

    @Test
    @DisplayName("Step F: Cross-User Session Isolation and Role Mapping (Student, Officer, Admin)")
    void testCrossUserSessionIsolationAndRoles() throws Exception {
        // User 1: PLACEMENT_OFFICER
        CsrfInfo csrfOfficer = obtainCsrf(null);
        String sidOfficerA = csrfOfficer.sessionId();

        String officerLoginPayload = "{\"username\":\"" + OFFICER_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        HttpResponse<String> offLoginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + sidOfficerA)
                .header(csrfOfficer.headerName(), csrfOfficer.token())
                .POST(HttpRequest.BodyPublishers.ofString(officerLoginPayload))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, offLoginRes.statusCode());
        String sidOfficerB = extractSessionId(offLoginRes);
        assertNotEquals(sidOfficerA, sidOfficerB);

        // User 2: ADMINISTRATOR (Independent session)
        CsrfInfo csrfAdmin = obtainCsrf(null);
        String sidAdminA = csrfAdmin.sessionId();
        assertNotEquals(sidOfficerB, sidAdminA, "Independent client session must have separate initial SID");

        String adminLoginPayload = "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        HttpResponse<String> admLoginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + sidAdminA)
                .header(csrfAdmin.headerName(), csrfAdmin.token())
                .POST(HttpRequest.BodyPublishers.ofString(adminLoginPayload))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, admLoginRes.statusCode());
        String sidAdminB = extractSessionId(admLoginRes);
        assertNotEquals(sidAdminA, sidAdminB);
        assertNotEquals(sidOfficerB, sidAdminB, "Officer and Admin must have distinct authenticated sessions");

        // Verify Officer identity
        HttpResponse<String> meOfficer = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidOfficerB)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meOfficer.statusCode());
        assertEquals("PLACEMENT_OFFICER", JsonPath.read(meOfficer.body(), "$.role"));

        // Verify Admin identity
        HttpResponse<String> meAdmin = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidAdminB)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meAdmin.statusCode());
        assertEquals("ADMINISTRATOR", JsonPath.read(meAdmin.body(), "$.role"));

        // Logout Officer, Admin remains active
        CsrfInfo offCsrfCurrent = obtainCsrf(sidOfficerB);
        httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/logout"))
                .header("Cookie", "JSESSIONID=" + sidOfficerB)
                .header(offCsrfCurrent.headerName(), offCsrfCurrent.token())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        // Officer is now 401
        HttpResponse<String> meOfficerAfter = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidOfficerB)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, meOfficerAfter.statusCode());

        // Admin is STILL 200 OK
        HttpResponse<String> meAdminStillActive = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + sidAdminB)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meAdminStillActive.statusCode());
    }

    @Test
    @DisplayName("CSRF Lifecycle: Missing, invalid, or cross-session CSRF tokens are rejected")
    void testCsrfProtectionEnforcement() throws Exception {
        CsrfInfo session1 = obtainCsrf(null);
        CsrfInfo session2 = obtainCsrf(null);

        String loginPayload = "{\"username\":\"" + STUDENT_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}";

        // 1. Missing CSRF token -> 403 Forbidden
        HttpResponse<String> missingCsrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + session1.sessionId())
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, missingCsrfRes.statusCode(), "Mutation without CSRF must return 403 Forbidden");

        // 2. Invalid CSRF token -> 403 Forbidden
        HttpResponse<String> badCsrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + session1.sessionId())
                .header("X-CSRF-TOKEN", "InvalidForgedToken99999")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, badCsrfRes.statusCode(), "Mutation with invalid CSRF token must return 403 Forbidden");

        // 3. CSRF token from Session 2 sent with Session 1 -> 403 Forbidden
        HttpResponse<String> crossCsrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + session1.sessionId())
                .header("X-CSRF-TOKEN", session2.token())
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, crossCsrfRes.statusCode(), "Cross-session CSRF token must return 403 Forbidden");
    }

    @Test
    @DisplayName("Account Revocation: Disabling an account in PostgreSQL immediately terminates active session")
    void testAccountDisablementTerminatesActiveSession() throws Exception {
        // Authenticate student
        CsrfInfo csrf = obtainCsrf(null);
        HttpResponse<String> loginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + csrf.sessionId())
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + STUDENT_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());
        String activeSid = extractSessionId(loginRes);

        // Verify session is active
        HttpResponse<String> beforeDisable = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + activeSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, beforeDisable.statusCode());

        // Simulate admin disabling the account directly in PostgreSQL
        Optional<UserAccount> accountOpt = userAccountRepository.findByUsername(STUDENT_USER);
        assertTrue(accountOpt.isPresent());
        UserAccount account = accountOpt.get();
        userAccountRepository.save(account.toBuilder().enabled(false).build());

        // Next request with activeSid MUST return 401 Unauthorized immediately
        HttpResponse<String> afterDisable = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + activeSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, afterDisable.statusCode(),
                "Mid-session account disablement in PostgreSQL must immediately invalidate authenticated session");
    }

    @Test
    @DisplayName("Account Lockout: Locking an account in PostgreSQL immediately terminates active session")
    void testAccountLockoutTerminatesActiveSession() throws Exception {
        // Authenticate officer
        CsrfInfo csrf = obtainCsrf(null);
        HttpResponse<String> loginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + csrf.sessionId())
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + OFFICER_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());
        String activeSid = extractSessionId(loginRes);

        // Simulate brute-force lockout: record failed attempt with lockedUntil in future
        Optional<UserAccount> accountOpt = userAccountRepository.findByUsername(OFFICER_USER);
        assertTrue(accountOpt.isPresent());
        UserAccount account = accountOpt.get();
        userAccountRepository.recordFailedAttempt(account.getId(), 5, Instant.now().plusSeconds(1800));

        // Next request with activeSid MUST return 401 Unauthorized immediately
        HttpResponse<String> afterLockout = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + activeSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, afterLockout.statusCode(),
                "Mid-session account lockout in PostgreSQL must immediately invalidate authenticated session");
    }

    @Test
    @DisplayName("Role Mutation Defense: Role change in PostgreSQL terminates session to prevent stale authority abuse")
    void testRoleChangeTerminatesSession() throws Exception {
        // Authenticate student
        CsrfInfo csrf = obtainCsrf(null);
        HttpResponse<String> loginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + csrf.sessionId())
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + STUDENT_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());
        String activeSid = extractSessionId(loginRes);

        // Mutate role from STUDENT to PLACEMENT_OFFICER in PostgreSQL (and clear studentId)
        Optional<UserAccount> accountOpt = userAccountRepository.findByUsername(STUDENT_USER);
        assertTrue(accountOpt.isPresent());
        UserAccount current = accountOpt.get();
        UserAccount mutated = current.toBuilder()
                .role(UserRole.PLACEMENT_OFFICER)
                .studentId(null)
                .build();
        userAccountRepository.save(mutated);

        // Next request must detect stale authority and terminate session
        HttpResponse<String> afterRoleChange = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + activeSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, afterRoleChange.statusCode(),
                "Role mutation in PostgreSQL must terminate session to prevent stale authorization privileges");
    }

    @Test
    @DisplayName("Cookie Security: HttpOnly, SameSite, and Cookie Path attributes are verified")
    void testCookieSecurityAttributes() throws Exception {
        CsrfInfo csrf = obtainCsrf(null);
        String sessionCookie = findSessionCookieHeader(
                httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString())
        );

        assertNotNull(sessionCookie, "Session cookie header must be present");
        assertTrue(sessionCookie.contains("HttpOnly") || sessionCookie.toLowerCase().contains("httponly"),
                "Session cookie must be HttpOnly to prevent JavaScript theft");
        assertTrue(sessionCookie.contains("SameSite=Lax") || sessionCookie.toLowerCase().contains("samesite=lax"),
                "Session cookie must have SameSite=Lax attribute");
        assertTrue(sessionCookie.contains("Path=/") || sessionCookie.toLowerCase().contains("path="),
                "Session cookie must specify Path attribute");
    }
}
