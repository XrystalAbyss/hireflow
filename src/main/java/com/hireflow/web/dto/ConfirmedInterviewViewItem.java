package com.hireflow.web.dto;

import com.hireflow.model.ApplicationStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Display-safe DTO representing a confirmed interview appointment from PostgreSQL interview_sessions.
 * Safe for rendering in officer timetable and student appointment views.
 */
public record ConfirmedInterviewViewItem(
        String sessionId,
        String applicationId,
        String studentId,
        String studentName,
        String studentBranch,
        String driveId,
        String jobTitle,
        String companyName,
        String interviewerId,
        String interviewerName,
        String roomId,
        String roomName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ApplicationStatus status
) implements Serializable {

    public String getSessionId() { return sessionId; }
    public String getApplicationId() { return applicationId; }
    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStudentBranch() { return studentBranch; }
    public String getDriveId() { return driveId; }
    public String getJobTitle() { return jobTitle; }
    public String getCompanyName() { return companyName; }
    public String getInterviewerId() { return interviewerId; }
    public String getInterviewerName() { return interviewerName; }
    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public ApplicationStatus getStatus() { return status; }

    public LocalDate getDate() {
        return startTime != null ? startTime.toLocalDate() : null;
    }

    public LocalTime getStartTimeOnly() {
        return startTime != null ? startTime.toLocalTime() : null;
    }

    public LocalTime getEndTimeOnly() {
        return endTime != null ? endTime.toLocalTime() : null;
    }
}
