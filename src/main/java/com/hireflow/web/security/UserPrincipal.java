package com.hireflow.web.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

/**
 * Immutable security principal representing an authenticated user identity.
 * Prepared for Stage 3C session authentication. Never constructed from unauthenticated client inputs.
 */
public final class UserPrincipal implements Principal, Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String displayName;
    private final UserRole role;
    private final String studentId; // Null if officer or administrator

    public UserPrincipal(String username, String displayName, UserRole role, String studentId) {
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : username;
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.studentId = studentId != null && !studentId.trim().isEmpty() ? studentId.trim().toUpperCase() : null;
    }

    public UserPrincipal(String username, UserRole role, String displayName, String studentId) {
        this(username, displayName, role, studentId);
    }

    @Override
    public String getName() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public String getStudentId() {
        return studentId;
    }

    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }

    public boolean isPlacementOfficer() {
        return role == UserRole.PLACEMENT_OFFICER || role == UserRole.ADMINISTRATOR;
    }

    public boolean isAdministrator() {
        return role == UserRole.ADMINISTRATOR;
    }

    public boolean isAdmin() {
        return isAdministrator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(username, that.username) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, role);
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
