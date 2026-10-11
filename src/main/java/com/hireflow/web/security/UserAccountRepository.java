package com.hireflow.web.security;

import com.hireflow.repository.CrudRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository interface for managing persistent {@link UserAccount} entities.
 */
public interface UserAccountRepository extends CrudRepository<UserAccount, String> {

    /**
     * Finds a user account by normalized username.
     */
    Optional<UserAccount> findByUsername(String username);

    /**
     * Finds a student user account by associated student ID.
     */
    Optional<UserAccount> findByStudentId(String studentId);

    /**
     * Updates the last login timestamp for the specified account.
     */
    void updateLastLogin(String userId, Instant loginTime);

    /**
     * Records an incremental failed login attempt and optional lockout expiry.
     */
    void recordFailedAttempt(String userId, int attempts, Instant lockedUntil);

    /**
     * Resets failed login attempts and clears any lockout.
     */
    void resetFailedAttempts(String userId);

    /**
     * Checks if a user account exists with the specified normalized username.
     */
    boolean existsByUsername(String username);

    /**
     * Deletes all accounts (primarily for testing in isolated environments).
     */
    void deleteAll();
}
