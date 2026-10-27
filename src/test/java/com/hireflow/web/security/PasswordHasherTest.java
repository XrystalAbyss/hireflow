package com.hireflow.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordHasher - BCrypt Cryptographic Hashing & Verification Tests")
class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        // Use cost 6 for fast unit tests while testing full BCrypt logic
        hasher = new PasswordHasher(6);
    }

    @Test
    @DisplayName("hashing: produces distinct salted BCrypt hashes for identical passwords")
    void testSaltingProducesDistinctHashes() {
        char[] pw = "SecurePassword123!".toCharArray();
        String hash1 = hasher.hashPassword(pw);
        String hash2 = hasher.hashPassword(pw);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, "BCrypt must generate unique per-password random salts");
        assertTrue(hash1.startsWith("$2a$") || hash1.startsWith("$2b$") || hash1.startsWith("$2y$"));
    }

    @Test
    @DisplayName("verification: correctly verifies matching password against hash")
    void testVerifyMatchingPassword() {
        char[] pw = "MySecretPassphrase2026".toCharArray();
        String hash = hasher.hashPassword(pw);

        assertTrue(hasher.verifyPassword(pw, hash));
        assertFalse(hasher.verifyPassword("WrongPassword123".toCharArray(), hash));
    }

    @Test
    @DisplayName("bounds: rejects password shorter than minimum length")
    void testRejectShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hashPassword("12345".toCharArray()));
    }

    @Test
    @DisplayName("bounds: rejects password longer than maximum length to prevent DoS")
    void testRejectExcessivelyLongPassword() {
        char[] longPw = new char[129];
        java.util.Arrays.fill(longPw, 'A');
        assertThrows(IllegalArgumentException.class, () -> hasher.hashPassword(longPw));
        assertFalse(hasher.verifyPassword(longPw, "$2a$06$fakehashstringfakehashstringfakehashstringfakehashstring"));
    }

    @Test
    @DisplayName("safety: handles null or empty inputs gracefully")
    void testNullAndEmptyHandling() {
        assertFalse(hasher.verifyPassword(null, "somehash"));
        assertFalse(hasher.verifyPassword(new char[0], null));
        assertFalse(hasher.verifyPassword("test".toCharArray(), ""));
    }
}
