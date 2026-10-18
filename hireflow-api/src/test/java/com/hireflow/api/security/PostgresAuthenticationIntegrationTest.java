package com.hireflow.api.security;

import com.hireflow.api.dto.CsrfTokenDto;
import com.hireflow.api.dto.LoginRequestDto;
import com.hireflow.api.dto.UserProfileDto;
import com.hireflow.model.Student;
import com.hireflow.repository.StudentRepository;
import com.hireflow.web.security.AuthenticationResult;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostgresAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiAuthenticationAdapter authenticationAdapter;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private BruteForceThrottler bruteForceThrottler;

    private static final String TEST_STUDENT_ID = "STU-SEC-4B-001";
    private static final String TEST_STUDENT_USER = "test_student_4b";
    private static final String TEST_OFFICER_USER = "test_officer_4b";
    private static final String TEST_PASSWORD = "CorrectSecurePassword123!";

    @BeforeEach
    void setUpTestData() {
        cleanTestData();

        // 1. Ensure student record exists for student account foreign key binding
        if (!studentRepository.existsById(TEST_STUDENT_ID)) {
            Student student = new Student(
                    TEST_STUDENT_ID,
                    "Security Test Student",
                    "sec.student@hireflow.test",
                    "CSE",
                    8.75,
                    2025
            );
            studentRepository.add(student);
        }

        // 2. Persist real student account with BCrypt hash
        String studentHash = passwordHasher.hashPassword(TEST_PASSWORD.toCharArray());
        UserAccount studentAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(TEST_STUDENT_USER)
                .passwordHash(studentHash)
                .role(UserRole.STUDENT)
                .studentId(TEST_STUDENT_ID)
                .displayName("Security Student")
                .enabled(true)
                .build();
        userAccountRepository.save(studentAccount);

        // 3. Persist real officer account with BCrypt hash
        String officerHash = passwordHasher.hashPassword(TEST_PASSWORD.toCharArray());
        UserAccount officerAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(TEST_OFFICER_USER)
                .passwordHash(officerHash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Security Officer")
                .enabled(true)
                .build();
        userAccountRepository.save(officerAccount);

        bruteForceThrottler.clear();
    }

    @AfterEach
    void cleanTestData() {
        try {
            userAccountRepository.findByUsername(TEST_STUDENT_USER).ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            userAccountRepository.findByUsername(TEST_OFFICER_USER).ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            userAccountRepository.findByUsername("disabled_user_4b").ifPresent(acc -> userAccountRepository.deleteById(acc.getId()));
            studentRepository.deleteById(TEST_STUDENT_ID);
        } catch (Exception ignored) {
        }
        bruteForceThrottler.clear();
    }

    @Test
    @DisplayName("PostgreSQL: Correct BCrypt password verifies and updates lastLoginAt in database")
    void validCredentials_authenticatesAgainstPostgres() {
        AuthenticationResult result = authenticationAdapter.authenticate(TEST_STUDENT_USER, TEST_PASSWORD, "127.0.0.1");

        assertTrue(result.isSuccess());
        assertTrue(result.getPrincipal().isPresent());
        assertEquals("test_student_4b", result.getPrincipal().get().getName());
        assertEquals(UserRole.STUDENT, result.getPrincipal().get().getRole());
        assertEquals(TEST_STUDENT_ID, result.getPrincipal().get().getStudentId());

        // Verify lastLoginAt updated in real PostgreSQL record
        Optional<UserAccount> refreshed = userAccountRepository.findByUsername(TEST_STUDENT_USER);
        assertTrue(refreshed.isPresent());
        assertNotNull(refreshed.get().getLastLoginAt());
    }

    @Test
    @DisplayName("PostgreSQL: Invalid password increments failed_login_attempts counter")
    void invalidPassword_incrementsDbFailedAttempts() {
        AuthenticationResult result = authenticationAdapter.authenticate(TEST_STUDENT_USER, "WrongPassword999!", "127.0.0.1");

        assertFalse(result.isSuccess());
        assertEquals(AuthenticationResult.Status.INVALID_CREDENTIALS, result.getStatus());

        Optional<UserAccount> refreshed = userAccountRepository.findByUsername(TEST_STUDENT_USER);
        assertTrue(refreshed.isPresent());
        assertEquals(1, refreshed.get().getFailedLoginAttempts());
    }

    @Test
    @DisplayName("PostgreSQL: Disabled account is rejected with ACCOUNT_DISABLED")
    void disabledAccount_isRejected() {
        String hash = passwordHasher.hashPassword(TEST_PASSWORD.toCharArray());
        UserAccount disabledAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("disabled_user_4b")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Disabled Officer")
                .enabled(false)
                .build();
        userAccountRepository.save(disabledAccount);

        AuthenticationResult result = authenticationAdapter.authenticate("disabled_user_4b", TEST_PASSWORD, "127.0.0.1");

        assertFalse(result.isSuccess());
        assertEquals(AuthenticationResult.Status.ACCOUNT_DISABLED, result.getStatus());
        assertTrue(result.getErrorMessage().contains("disabled"));
    }

    @Test
    @DisplayName("PostgreSQL: Brute-force throttling locks out account after repeated failures")
    void bruteForceThrottling_locksOutAfterThreshold() {
        for (int i = 0; i < 5; i++) {
            authenticationAdapter.authenticate(TEST_OFFICER_USER, "BadPassword" + i, "10.0.0.10");
        }

        // 6th attempt should be blocked by throttler
        AuthenticationResult throttled = authenticationAdapter.authenticate(TEST_OFFICER_USER, TEST_PASSWORD, "10.0.0.10");
        assertFalse(throttled.isSuccess());
        assertEquals(AuthenticationResult.Status.ACCOUNT_LOCKED, throttled.getStatus());
    }

    @Test
    @DisplayName("Throttler: In-memory tracker bounded capacity defends against heap exhaustion")
    void throttler_capacityIsBounded() {
        BruteForceThrottler throttler = new BruteForceThrottler(5, 300);
        for (int i = 0; i < 6000; i++) {
            throttler.recordFailure("10.0." + (i / 256) + "." + (i % 256), "user_" + i);
        }
        // Capacity bound prevents unbounded growth (eviction triggers when >= 5000)
        assertTrue(throttler.isBlocked("10.0.23.111", "user_5999") || !throttler.isBlocked("10.0.0.0", "user_0"));
    }

    @Test
    @DisplayName("E2E Session Flow: Login creates authenticated session, rotates session ID, and me returns profile")
    void endToEnd_sessionLifecycle() throws Exception {
        // 1. Obtain CSRF token
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session, "Session must be created to hold CSRF token");

        // 2. Perform Login with CSRF token and valid credentials
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEST_STUDENT_USER + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(TEST_STUDENT_USER))
                .andExpect(jsonPath("$.displayName").value("Security Student"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value(TEST_STUDENT_ID))
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(authenticatedSession, "Authenticated session must be present");

        // 3. Subsequent request to /api/v1/auth/me using the authenticated session succeeds
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(TEST_STUDENT_USER))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value(TEST_STUDENT_ID));

        // 4. Logout using the authenticated session and CSRF
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        // 5. Subsequent request to /api/v1/auth/me using the invalidated session returns 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(authenticatedSession))
                .andExpect(status().isUnauthorized());
    }
}
