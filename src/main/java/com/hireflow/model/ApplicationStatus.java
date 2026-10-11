package com.hireflow.model;

/**
 * Lifecycle states of a student's job application.
 */
public enum ApplicationStatus {
    APPLIED("Applied"),
    ELIGIBLE("Eligible"),
    INELIGIBLE("Ineligible"),
    SHORTLISTED("Shortlisted"),
    SCHEDULED("Scheduled"),
    REJECTED("Rejected"),
    SELECTED("Selected");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
