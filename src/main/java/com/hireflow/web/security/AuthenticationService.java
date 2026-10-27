package com.hireflow.web.security;

import com.hireflow.repository.StudentRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Authentication and credential verification service.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Zero disclosure of username existence on failed attempts (generic error messages).</li>
 *     <li>Adaptive BCrypt verification with constant-time equality comparisons.</li>
 *     <li>Protection against brute-force attacks via {@link BruteForceThrottler} and DB attempt counters.</li>
 *     <li>Verifies student identity link against {@link StudentRepository} for STUDENT roles.</li>
 *     <li>Password characters are validated and never written to logs or exceptions.</li>
 * </ul>
 */
public class AuthenticationService {

    private static final Logger LOGGER = Logger.getLogger(AuthenticationService.class.getName());
    public static final int NEW_ACCOUNT_MIN_PASSWORD_LENGTH = 12;

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final StudentRepository studentRepository;
    private final BruteForceThrottler throttler;

    public AuthenticationService(UserAccountRepository userAccountRepository,
                                 PasswordHasher passwordHasher,
                                 StudentRepository studentRepository,
                                 BruteForceThrottler throttler) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository, "userAccountRepository cannot be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher cannot be null");
        this.studentRepository = studentRepository; // May be null in decoupled contexts
        this.throttler = throttler != null ? throttler : new BruteForceThrottler();
    }

    /**
     * Authenticates a user against persistent credentials.
     *
     * @param username Candidate username.
     * @param password Candidate password characters.
     * @param clientIp Client IP address for rate-limiting.
     * @return AuthenticationResult indicating success or generic failure.
     */
    public AuthenticationResult authenticate(String username, char[] password, String clientIp) {
        // 1. Check brute-force throttling first
        if (throttler.isBlocked(clientIp, username)) {
            LOGGER.warning("[Auth] Throttled login attempt for user/ip: " + clientIp);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.ACCOUNT_LOCKED,
                    "Too many failed login attempts. Please wait a few minutes before trying again."
            );
        }

        // 2. Validate basic input constraints
        if (username == null || username.trim().isEmpty() || password == null || password.length == 0) {
            throttler.recordFailure(clientIp, username);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.INVALID_CREDENTIALS,
                    "Invalid username or password."
            );
        }

        if (password.length > PasswordHasher.MAX_PASSWORD_LENGTH) {
            throttler.recordFailure(clientIp, username);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.INVALID_CREDENTIALS,
                    "Invalid username or password."
            );
        }

        String normalizedUsername;
        try {
            normalizedUsername = UserAccount.normalizeUsername(username);
        } catch (IllegalArgumentException ex) {
            throttler.recordFailure(clientIp, username);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.INVALID_CREDENTIALS,
                    "Invalid username or password."
            );
        }

        // 3. Retrieve account from repository
        Optional<UserAccount> optAccount = userAccountRepository.findByUsername(normalizedUsername);
        if (optAccount.isEmpty()) {
            throttler.recordFailure(clientIp, normalizedUsername);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.INVALID_CREDENTIALS,
                    "Invalid username or password."
            );
        }

        UserAccount account = optAccount.get();

        // 4. Check account status (enabled & lockout)
        if (!account.isEnabled()) {
            throttler.recordFailure(clientIp, normalizedUsername);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.ACCOUNT_DISABLED,
                    "Account is disabled. Please contact your campus placement cell."
            );
        }

        Instant now = Instant.now();
        if (account.isAccountLocked(now)) {
            throttler.recordFailure(clientIp, normalizedUsername);
            return AuthenticationResult.failure(
                    AuthenticationResult.Status.ACCOUNT_LOCKED,
                    "Too many failed login attempts. Please wait a few minutes before trying again."
            );
        }

        // 5. Verify linked student record if role is STUDENT
        if (account.getRole() == UserRole.STUDENT) {
            if (studentRepository != null && !studentRepository.existsById(account.getStudentId())) {
                throttler.recordFailure(clientIp, normalizedUsername);
                return AuthenticationResult.failure(
                    AuthenticationResult.Status.STUDENT_UNLINKED,
                    "Student record linked to this account could not be found."
                );
            }
        }

        // 6. Verify password hash using BCrypt in constant time
        boolean matches = passwordHasher.verifyPassword(password, account.getPasswordHash());
        if (!matches) {
            int newAttempts = account.getFailedLoginAttempts() + 1;
            Instant lockoutExpiry = (newAttempts >= BruteForceThrottler.DEFAULT_MAX_ATTEMPTS)
                    ? now.plusSeconds(BruteForceThrottler.DEFAULT_LOCKOUT_SECONDS)
                    : null;

            userAccountRepository.recordFailedAttempt(account.getId(), newAttempts, lockoutExpiry);
            throttler.recordFailure(clientIp, normalizedUsername);

            return AuthenticationResult.failure(
                    AuthenticationResult.Status.INVALID_CREDENTIALS,
                    "Invalid username or password."
            );
        }

        // 7. Authentication Success: Reset failure tracking & record login
        userAccountRepository.updateLastLogin(account.getId(), now);
        throttler.recordSuccess(clientIp, normalizedUsername);

        return AuthenticationResult.success(account.toPrincipal());
    }

    /**
     * Provisions a new user account with secure password hashing and integrity validation.
     *
     * @param username    Unique account username.
     * @param password    Plaintext password to hash.
     * @param role        Authoritative user role.
     * @param studentId   Mandatory student ID if role is STUDENT, null otherwise.
     * @param displayName Human-readable display name.
     * @return Persisted UserAccount.
     */
    public UserAccount provisionAccount(String username, char[] password, UserRole role, String studentId, String displayName) {
        String normalized = UserAccount.normalizeUsername(username);
        if (userAccountRepository.existsByUsername(normalized)) {
            throw new IllegalArgumentException("User account already exists with username: " + normalized);
        }

        Objects.requireNonNull(role, "UserRole cannot be null");
        passwordHasher.validatePasswordInput(password);
        if (password.length < NEW_ACCOUNT_MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New user password must be at least " + NEW_ACCOUNT_MIN_PASSWORD_LENGTH + " characters long");
        }

        if (role == UserRole.STUDENT) {
            if (studentId == null || studentId.trim().isEmpty()) {
                throw new IllegalArgumentException("STUDENT account requires a non-empty studentId");
            }
            if (studentRepository != null && !studentRepository.existsById(studentId.trim().toUpperCase())) {
                throw new IllegalArgumentException("No existing student record found with ID: " + studentId);
            }
        } else {
            if (studentId != null && !studentId.trim().isEmpty()) {
                throw new IllegalArgumentException("Non-STUDENT accounts cannot have a studentId");
            }
        }

        String passwordHash = passwordHasher.hashPassword(password);
        UserAccount account = UserAccount.builder()
                .username(normalized)
                .passwordHash(passwordHash)
                .role(role)
                .studentId(studentId)
                .displayName(displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : normalized)
                .enabled(true)
                .build();

        return userAccountRepository.save(account);
    }

    public UserAccountRepository getUserAccountRepository() {
        return userAccountRepository;
    }

    public PasswordHasher getPasswordHasher() {
        return passwordHasher;
    }

    public BruteForceThrottler getThrottler() {
        return throttler;
    }
}
