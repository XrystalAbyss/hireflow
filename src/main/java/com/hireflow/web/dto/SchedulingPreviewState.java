package com.hireflow.web.dto;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable server-side preview state representing a proposed interview schedule generated
 * by {@link com.hireflow.scheduler.GreedyScheduler}.
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Retained exclusively in server-side session memory with bounded lifecycle.</li>
 *     <li>Bound to the authenticated placement officer username.</li>
 *     <li>Generated with a cryptographically unpredictable 128-bit preview token.</li>
 *     <li>Configurable expiration duration (default 15 minutes) prevents stale or replayed publications.</li>
 *     <li>Tracks one-time publication consumption to prevent replay or multi-submission side effects.</li>
 *     <li>Contains display-safe metadata without exposing sensitive internal credentials.</li>
 *     <li>Advisory only: does not mutate database records until explicitly published through transactional publication endpoint.</li>
 * </ul>
 */
public record SchedulingPreviewState(
        String previewId,
        String officerUsername,
        Instant createdAt,
        Instant expiresAt,
        boolean consumed,
        String driveId,
        String jobTitle,
        String companyName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        int bufferMinutes,
        int totalRequested,
        int totalScheduled,
        int totalUnscheduled,
        List<ProposedSessionDto> proposedSessions,
        List<UnscheduledCandidateDto> unscheduledCandidates
) implements Serializable {

    public static final Duration DEFAULT_MAX_AGE = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generatePreviewId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return "PREV-" + HexFormat.of().formatHex(bytes).toUpperCase();
    }

    public SchedulingPreviewState {
        Objects.requireNonNull(previewId, "previewId cannot be null");
        Objects.requireNonNull(officerUsername, "officerUsername cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        proposedSessions = proposedSessions != null ? Collections.unmodifiableList(proposedSessions) : List.of();
        unscheduledCandidates = unscheduledCandidates != null ? Collections.unmodifiableList(unscheduledCandidates) : List.of();
    }

    /**
     * Backward-compatible 16-parameter constructor defaulting to 15-minute expiration and unconsumed state.
     */
    public SchedulingPreviewState(
            String previewId,
            String officerUsername,
            Instant createdAt,
            String driveId,
            String jobTitle,
            String companyName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int durationMinutes,
            int bufferMinutes,
            int totalRequested,
            int totalScheduled,
            int totalUnscheduled,
            List<ProposedSessionDto> proposedSessions,
            List<UnscheduledCandidateDto> unscheduledCandidates
    ) {
        this(
                previewId,
                officerUsername,
                createdAt,
                createdAt.plus(DEFAULT_MAX_AGE),
                false,
                driveId,
                jobTitle,
                companyName,
                date,
                startTime,
                endTime,
                durationMinutes,
                bufferMinutes,
                totalRequested,
                totalScheduled,
                totalUnscheduled,
                proposedSessions,
                unscheduledCandidates
        );
    }

    public String getPreviewId() { return previewId; }
    public String getOfficerUsername() { return officerUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumed; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
    public String getDriveId() { return driveId; }
    public String getJobTitle() { return jobTitle; }
    public String getCompanyName() { return companyName; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public int getTotalRequested() { return totalRequested; }
    public int getTotalScheduled() { return totalScheduled; }
    public int getTotalUnscheduled() { return totalUnscheduled; }
    public List<ProposedSessionDto> getProposedSessions() { return proposedSessions; }
    public List<UnscheduledCandidateDto> getUnscheduledCandidates() { return unscheduledCandidates; }

    public SchedulingPreviewState markConsumed() {
        return new SchedulingPreviewState(
                this.previewId,
                this.officerUsername,
                this.createdAt,
                this.expiresAt,
                true,
                this.driveId,
                this.jobTitle,
                this.companyName,
                this.date,
                this.startTime,
                this.endTime,
                this.durationMinutes,
                this.bufferMinutes,
                this.totalRequested,
                this.totalScheduled,
                this.totalUnscheduled,
                this.proposedSessions,
                this.unscheduledCandidates
        );
    }

    public record ProposedSessionDto(
            String sessionId,
            String applicationId,
            String studentId,
            String studentName,
            String studentBranch,
            String interviewerId,
            String interviewerName,
            String roomId,
            String roomName,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) implements Serializable {
        public String getSessionId() { return sessionId; }
        public String getApplicationId() { return applicationId; }
        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getStudentBranch() { return studentBranch; }
        public String getInterviewerId() { return interviewerId; }
        public String getInterviewerName() { return interviewerName; }
        public String getRoomId() { return roomId; }
        public String getRoomName() { return roomName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
    }

    public record UnscheduledCandidateDto(
            String applicationId,
            String studentId,
            String studentName,
            String studentBranch,
            String reason
    ) implements Serializable {
        public String getApplicationId() { return applicationId; }
        public String getStudentId() { return studentId; }
        public String getStudentName() { return studentName; }
        public String getStudentBranch() { return studentBranch; }
        public String getReason() { return reason; }
    }
}
