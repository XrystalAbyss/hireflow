package com.hireflow.scheduler;

import com.hireflow.model.Application;

import java.util.Objects;

/**
 * Encapsulates an application that could not be scheduled, along with the explicit reason.
 */
public record UnscheduledApplication(Application application, String reason) {

    public UnscheduledApplication {
        Objects.requireNonNull(application, "Application cannot be null.");
        reason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Unspecified scheduling conflict.";
    }

    public String getApplicationId() {
        return application.getId();
    }

    public String getStudentId() {
        return application.getStudentId();
    }

    public String getDriveId() {
        return application.getDriveId();
    }
}
