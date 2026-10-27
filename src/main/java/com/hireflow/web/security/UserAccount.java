package com.hireflow.web.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an authenticated user account.
 * Enforces strict role and student-identity binding invariants.
 */
public final class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final String studentId;
    private final String displayName;
    private final boolean enabled;
    private final int failedLoginAttempts;
    private final Instant lockedUntil;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastLoginAt;

    public UserAccount(Builder builder) {
        this.id = builder.id != null ? builder.id.trim().toLowerCase() : UUID.randomUUID().toString().toLowerCase();
        this.username = normalizeUsername(builder.username);
        this.passwordHash = Objects.requireNonNull(builder.passwordHash, "passwordHash cannot be null");
        this.role = Objects.requireNonNull(builder.role, "role cannot be null");
        this.studentId = builder.studentId != null && !builder.studentId.trim().isEmpty()
                ? builder.studentId.trim().toUpperCase()
                : null;
        this.displayName = builder.displayName != null && !builder.displayName.trim().isEmpty()
                ? builder.displayName.trim()
                : this.username;
        this.enabled = builder.enabled;
        this.failedLoginAttempts = Math.max(0, builder.failedLoginAttempts);
        this.lockedUntil = builder.lockedUntil;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.lastLoginAt = builder.lastLoginAt;

        // Invariant: Student accounts must have a linked studentId
        if (this.role == UserRole.STUDENT && this.studentId == null) {
            throw new IllegalArgumentException("A STUDENT account must link to a valid studentId");
        }
        // Invariant: Non-student accounts cannot have a studentId
        if (this.role != UserRole.STUDENT && this.studentId != null) {
            throw new IllegalArgumentException("Non-STUDENT accounts cannot have a linked studentId");
        }
    }

    public static String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        String normalized = username.trim().toLowerCase();
        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new IllegalArgumentException("Username length must be between 3 and 50 characters; received: " + normalized.length());
        }
        return normalized;
    }

    public UserPrincipal toPrincipal() {
        return new UserPrincipal(username, displayName, role, studentId);
    }

    public boolean isAccountLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .username(username)
                .passwordHash(passwordHash)
                .role(role)
                .studentId(studentId)
                .displayName(displayName)
                .enabled(enabled)
                .failedLoginAttempts(failedLoginAttempts)
                .lockedUntil(lockedUntil)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .lastLoginAt(lastLoginAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String username;
        private String passwordHash;
        private UserRole role;
        private String studentId;
        private String displayName;
        private boolean enabled = true;
        private int failedLoginAttempts = 0;
        private Instant lockedUntil;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastLoginAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder role(UserRole role) {
            this.role = role;
            return this;
        }

        public Builder studentId(String studentId) {
            this.studentId = studentId;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder failedLoginAttempts(int failedLoginAttempts) {
            this.failedLoginAttempts = failedLoginAttempts;
            return this;
        }

        public Builder lockedUntil(Instant lockedUntil) {
            this.lockedUntil = lockedUntil;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder lastLoginAt(Instant lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
            return this;
        }

        public UserAccount build() {
            return new UserAccount(this);
        }
    }
}
