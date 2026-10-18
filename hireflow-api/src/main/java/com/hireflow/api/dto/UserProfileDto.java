package com.hireflow.api.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Sanitized profile representation of an authenticated principal.
 * Never leaks password hashes, database IDs, or internal sensitive data.
 */
public record UserProfileDto(
        String username,
        String displayName,
        String role,
        String studentId,
        Instant lastLoginAt
) implements Serializable {}
