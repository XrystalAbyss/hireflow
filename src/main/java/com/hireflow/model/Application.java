package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a student's application to a recruitment drive.
 */
public class Application {
    private final String id;
    private final String studentId;
    private final String driveId;
    private ApplicationStatus status;
    private LocalDateTime appliedAt;
    private String remarks;

    public Application(String id, String studentId, String driveId, ApplicationStatus status) {
        this(id, studentId, driveId, status, LocalDateTime.now(), "");
    }

    public Application(String id, String studentId, String driveId, LocalDateTime appliedAt,
                       ApplicationStatus status, String remarks) {
        this(id, studentId, driveId, status, appliedAt, remarks);
    }

    public Application(String id, String studentId, String driveId, ApplicationStatus status,
                       LocalDateTime appliedAt, String remarks) {
        validateId(id);
        validateStudentId(studentId);
        validateDriveId(driveId);
        validateStatus(status);

        this.id = id.trim().toUpperCase();
        this.studentId = studentId.trim().toUpperCase();
        this.driveId = driveId.trim().toUpperCase();
        this.status = status;
        this.appliedAt = appliedAt != null ? appliedAt : LocalDateTime.now();
        this.remarks = remarks != null ? remarks.trim() : "";
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or blank.");
        }
    }

    public static void validateStudentId(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new ValidationException("Student ID cannot be null or blank.");
        }
    }

    public static void validateDriveId(String driveId) {
        if (driveId == null || driveId.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or blank.");
        }
    }

    public static void validateStatus(ApplicationStatus status) {
        if (status == null) {
            throw new ValidationException("Application status cannot be null.");
        }
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getDriveId() {
        return driveId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        validateStatus(status);
        this.status = status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt != null ? appliedAt : LocalDateTime.now();
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks != null ? remarks.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Application that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Application{" +
                "id='" + id + '\'' +
                ", studentId='" + studentId + '\'' +
                ", driveId='" + driveId + '\'' +
                ", status=" + status +
                ", appliedAt=" + appliedAt +
                '}';
    }
}
