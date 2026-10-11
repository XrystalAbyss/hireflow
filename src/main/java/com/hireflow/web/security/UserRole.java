package com.hireflow.web.security;

/**
 * Enumeration of authorization roles defined in HireFlow.
 * Designed for Stage 3 protected portal access control.
 */
public enum UserRole {
    STUDENT("Student Candidate"),
    PLACEMENT_OFFICER("Training & Placement Officer"),
    ADMINISTRATOR("System Administrator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
