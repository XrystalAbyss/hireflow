package com.hireflow.api.dto;

import com.hireflow.model.Application;

import java.time.LocalDateTime;

/**
 * Immutable Data Transfer Object representing detailed application information.
 */
public record StudentApplicationDetailDto(
        String id,
        String studentId,
        String driveId,
        String companyId,
        String companyName,
        String jobTitle,
        String status,
        LocalDateTime appliedAt,
        String remarks
) {
    public static StudentApplicationDetailDto fromDomain(Application app, String companyId, String companyName, String jobTitle) {
        if (app == null) return null;
        return new StudentApplicationDetailDto(
                app.getId(),
                app.getStudentId(),
                app.getDriveId(),
                companyId,
                companyName != null ? companyName : "Corporate Partner",
                jobTitle != null ? jobTitle : "Placement Drive",
                app.getStatus() != null ? app.getStatus().name() : null,
                app.getAppliedAt(),
                app.getRemarks()
        );
    }
}
