package com.hireflow.api.dto.officer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Student;

import java.time.LocalDateTime;

public record OfficerApplicationReviewDto(
        String id,
        String studentId,
        String studentName,
        String studentEmail,
        String branch,
        double cgpa,
        String driveId,
        String jobTitle,
        String companyName,
        ApplicationStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime appliedAt,
        String remarks
) {
    public static OfficerApplicationReviewDto fromDomain(
            Application app,
            Student student,
            String jobTitle,
            String companyName) {
        return new OfficerApplicationReviewDto(
                app.getId(),
                app.getStudentId(),
                student != null ? student.getName() : "Unknown Student",
                student != null ? student.getEmail() : "unknown@hireflow.local",
                student != null ? student.getBranch() : "N/A",
                student != null ? student.getCgpa() : 0.0,
                app.getDriveId(),
                jobTitle != null ? jobTitle : app.getDriveId(),
                companyName != null ? companyName : "Corporate Partner",
                app.getStatus(),
                app.getAppliedAt(),
                app.getRemarks()
        );
    }
}
