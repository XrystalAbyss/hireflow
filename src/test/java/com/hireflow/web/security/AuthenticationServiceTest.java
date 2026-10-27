package com.hireflow.web.security;

import com.hireflow.model.Student;
import com.hireflow.repository.InMemoryStudentRepository;
import com.hireflow.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthenticationService - Credentials Verification & Provisioning Tests")
class AuthenticationServiceTest {

    private UserAccountRepository userAccountRepository;
    private StudentRepository studentRepository;
    private PasswordHasher passwordHasher;
    private BruteForceThrottler throttler;
    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        userAccountRepository = new InMemoryUserAccountRepository();
        studentRepository = new InMemoryStudentRepository();
        passwordHasher = new PasswordHasher(6); // Fast cost for testing
        throttler = new BruteForceThrottler(5, 60L);
        authService = new AuthenticationService(userAccountRepository, passwordHasher, studentRepository, throttler);

        // Preload sample student
        Student student = new Student("STU-101", "Rahul Sharma", "rahul@univ.edu", "CSE", 8.8, 2026, true);
        studentRepository.add(student);
    }

    @Test
    @DisplayName("provisioning: provisions student account bound to verified student ID")
    void testProvisionStudentAccount() {
        UserAccount account = authService.provisionAccount(
                "rahul.sharma",
                "Password123!".toCharArray(),
                UserRole.STUDENT,
                "STU-101",
                "Rahul Sharma"
        );

        assertNotNull(account.getId());
        assertEquals("rahul.sharma", account.getUsername());
        assertEquals("STU-101", account.getStudentId());
        assertEquals(UserRole.STUDENT, account.getRole());
        assertTrue(account.isEnabled());
        assertNotNull(account.getPasswordHash());
    }

    @Test
    @DisplayName("provisioning: rejects student account when student record does not exist")
    void testRejectStudentAccountWithNonExistentStudent() {
        assertThrows(IllegalArgumentException.class, () ->
                authService.provisionAccount("ghost", "SecretPassword123!".toCharArray(), UserRole.STUDENT, "NON-EXISTENT", "Ghost"));
    }

    @Test
    @DisplayName("provisioning: rejects non-student account with a studentId")
    void testRejectNonStudentWithStudentId() {
        assertThrows(IllegalArgumentException.class, () ->
                authService.provisionAccount("officer1", "SecretPassword123!".toCharArray(), UserRole.PLACEMENT_OFFICER, "STU-101", "Officer 1"));
    }

    @Test
    @DisplayName("provisioning: rejects password shorter than 12 characters")
    void testRejectShortPasswordOnProvisioning() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.provisionAccount("shorty", "Short12345".toCharArray(), UserRole.PLACEMENT_OFFICER, null, "Short"));
        assertTrue(ex.getMessage().contains("at least 12"));
    }

    @Test
    @DisplayName("authentication: succeeds with correct credentials and returns sanitized principal")
    void testSuccessfulAuthentication() {
        authService.provisionAccount("rahul.sharma", "CorrectPassword123".toCharArray(), UserRole.STUDENT, "STU-101", "Rahul Sharma");

        AuthenticationResult result = authService.authenticate("rahul.sharma", "CorrectPassword123".toCharArray(), "127.0.0.1");

        assertTrue(result.isSuccess());
        assertTrue(result.getPrincipal().isPresent());

        UserPrincipal principal = result.getPrincipal().get();
        assertEquals("rahul.sharma", principal.getName());
        assertEquals("STU-101", principal.getStudentId());
        assertEquals(UserRole.STUDENT, principal.getRole());
        assertTrue(principal.isStudent());
        assertFalse(principal.isPlacementOfficer());
    }

    @Test
    @DisplayName("authentication: rejects incorrect password with generic error message")
    void testIncorrectPasswordRejection() {
        authService.provisionAccount("officer.admin", "StrongPass123!".toCharArray(), UserRole.PLACEMENT_OFFICER, null, "Director TPC");

        AuthenticationResult result = authService.authenticate("officer.admin", "WrongPassword".toCharArray(), "127.0.0.1");

        assertFalse(result.isSuccess());
        assertEquals(AuthenticationResult.Status.INVALID_CREDENTIALS, result.getStatus());
        assertEquals("Invalid username or password.", result.getErrorMessage());
    }

    @Test
    @DisplayName("authentication: rejects unknown username with identical generic error message")
    void testUnknownUsernameRejection() {
        AuthenticationResult result = authService.authenticate("unknown.user", "AnyPassword123".toCharArray(), "127.0.0.1");

        assertFalse(result.isSuccess());
        assertEquals(AuthenticationResult.Status.INVALID_CREDENTIALS, result.getStatus());
        assertEquals("Invalid username or password.", result.getErrorMessage());
    }

    @Test
    @DisplayName("authentication: rejects disabled account")
    void testDisabledAccountRejection() {
        UserAccount account = authService.provisionAccount("disabled.student", "ValidPass123".toCharArray(), UserRole.STUDENT, "STU-101", "Disabled");
        // Disable account in repo
        userAccountRepository.save(account.toBuilder().enabled(false).build());

        AuthenticationResult result = authService.authenticate("disabled.student", "ValidPass123".toCharArray(), "127.0.0.1");

        assertFalse(result.isSuccess());
        assertEquals(AuthenticationResult.Status.ACCOUNT_DISABLED, result.getStatus());
    }

    @Test
    @DisplayName("security: repeated failures trigger temporary brute-force lockout")
    void testRepeatedFailuresTriggerLockout() {
        authService.provisionAccount("alice", "AlicePass123".toCharArray(), UserRole.STUDENT, "STU-101", "Alice");

        for (int i = 0; i < 5; i++) {
            AuthenticationResult res = authService.authenticate("alice", "BadPass".toCharArray(), "10.0.0.5");
            assertFalse(res.isSuccess());
        }

        // 6th attempt is throttled
        AuthenticationResult lockedRes = authService.authenticate("alice", "AlicePass123".toCharArray(), "10.0.0.5");
        assertFalse(lockedRes.isSuccess());
        assertEquals(AuthenticationResult.Status.ACCOUNT_LOCKED, lockedRes.getStatus());
    }
}
