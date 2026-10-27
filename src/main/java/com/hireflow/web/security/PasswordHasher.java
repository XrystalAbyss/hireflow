package com.hireflow.web.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Objects;

/**
 * Enterprise cryptographic password hashing service using BCrypt.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Adaptive work factor with configurable cost parameter (default 10 log-rounds).</li>
 *     <li>Cryptographically secure random per-password salt managed internally by BCrypt.</li>
 *     <li>Constant-time hash comparison to prevent timing side-channel attacks.</li>
 *     <li>Bounded input length (max 128 characters) to defend against hashing denial-of-service.</li>
 *     <li>Zero logging or leakage of plaintext password characters.</li>
 * </ul>
 */
public class PasswordHasher {

    public static final int DEFAULT_COST = 10;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MIN_PASSWORD_LENGTH = 6;

    private final int cost;

    public PasswordHasher() {
        this(DEFAULT_COST);
    }

    public PasswordHasher(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("BCrypt cost factor must be between 4 and 31; provided: " + cost);
        }
        this.cost = cost;
    }

    /**
     * Generates a salted BCrypt hash from the supplied plaintext password.
     *
     * @param password Plaintext password characters.
     * @return 60-character standard BCrypt hash string.
     */
    public String hashPassword(char[] password) {
        validatePasswordInput(password);
        return BCrypt.withDefaults().hashToString(cost, password);
    }

    /**
     * Verifies a candidate password against an existing BCrypt hash in constant time.
     *
     * @param password Plaintext candidate password characters.
     * @param hash     Stored BCrypt hash string.
     * @return true if valid and matches; false otherwise.
     */
    public boolean verifyPassword(char[] password, String hash) {
        if (password == null || hash == null || hash.trim().isEmpty()) {
            return false;
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(password, hash);
            return result.verified;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validates that the provided password meets length and bounds constraints.
     */
    public void validatePasswordInput(char[] password) {
        Objects.requireNonNull(password, "Password characters cannot be null");
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password exceeds maximum allowed length of " + MAX_PASSWORD_LENGTH + " characters");
        }
    }

    public int getCost() {
        return cost;
    }
}
