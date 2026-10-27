package com.hireflow.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Immutable view model representing the outcome of an interview timetable publication execution.
 * Differentiates clearly between confirmed appointments, skipped duplicate appointments,
 * status/eligibility invalidations, resource conflicts (including buffer violations),
 * and unexpected infrastructure failures.
 */
public record PublicationSummaryState(
        String previewId,
        String driveId,
        String jobTitle,
        String companyName,
        Instant publishedAt,
        int publishedCount,
        int skippedDuplicatesCount,
        int conflictsCount,
        int errorsCount,
        List<PublishedSessionDetail> publishedDetails,
        List<String> skippedDuplicateApplicationIds,
        List<ConflictDetail> conflictDetails,
        List<String> infrastructureErrors
) implements Serializable {

    public PublicationSummaryState {
        publishedDetails = publishedDetails != null ? Collections.unmodifiableList(publishedDetails) : List.of();
        skippedDuplicateApplicationIds = skippedDuplicateApplicationIds != null ? Collections.unmodifiableList(skippedDuplicateApplicationIds) : List.of();
        conflictDetails = conflictDetails != null ? Collections.unmodifiableList(conflictDetails) : List.of();
        infrastructureErrors = infrastructureErrors != null ? Collections.unmodifiableList(infrastructureErrors) : List.of();
    }

    public boolean isCompletelySuccessful() {
        return conflictsCount == 0 && errorsCount == 0 && skippedDuplicatesCount == 0 && publishedCount > 0;
    }

    public boolean isPartiallySuccessful() {
        return publishedCount > 0 && (conflictsCount > 0 || errorsCount > 0 || skippedDuplicatesCount > 0);
    }

    public boolean hasConflictsOrErrors() {
        return conflictsCount > 0 || errorsCount > 0;
    }

    public String getPreviewId() { return previewId; }
    public String getDriveId() { return driveId; }
    public String getJobTitle() { return jobTitle; }
    public String getCompanyName() { return companyName; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublishedCount() { return publishedCount; }
    public int getSkippedDuplicatesCount() { return skippedDuplicatesCount; }
    public int getConflictsCount() { return conflictsCount; }
    public int getErrorsCount() { return errorsCount; }
    public List<PublishedSessionDetail> getPublishedDetails() { return publishedDetails; }
    public List<String> getSkippedDuplicateApplicationIds() { return skippedDuplicateApplicationIds; }
    public List<ConflictDetail> getConflictDetails() { return conflictDetails; }
    public List<String> getInfrastructureErrors() { return infrastructureErrors; }

    public record PublishedSessionDetail(
            String sessionId,
            String applicationId,
            String studentId,
            String studentName,
            String interviewerName,
            String roomName,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) implements Serializable {
        public String getSessionId() { return sessionId; }
        public String getApplicationId() { return applicationId; }
        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getInterviewerName() { return interviewerName; }
        public String getRoomName() { return roomName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
    }

    public record ConflictDetail(
            String applicationId,
            String reason,
            ConflictCategory category
    ) implements Serializable {
        public String getApplicationId() { return applicationId; }
        public String getReason() { return reason; }
        public ConflictCategory getCategory() { return category; }
    }

    public enum ConflictCategory {
        STATUS_NOT_SHORTLISTED,
        STUDENT_OVERLAP,
        INTERVIEWER_OVERLAP,
        ROOM_OVERLAP,
        BUFFER_VIOLATION,
        SESSION_EXISTS,
        RESOURCE_NOT_FOUND,
        OTHER_CONFLICT
    }
}
