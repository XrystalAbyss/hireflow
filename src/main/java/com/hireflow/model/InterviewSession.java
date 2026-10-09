package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an allocated interview session.
 */
public class InterviewSession {
    private final String id;
    private final String applicationId;
    private String interviewerId;
    private String roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public InterviewSession(String id, String applicationId, String interviewerId, String roomId,
                            LocalDateTime startTime, LocalDateTime endTime) {
        validateId(id);
        validateApplicationId(applicationId);
        validateInterviewerId(interviewerId);
        validateRoomId(roomId);
        validateTimeInterval(startTime, endTime);

        this.id = id.trim().toUpperCase();
        this.applicationId = applicationId.trim().toUpperCase();
        this.interviewerId = interviewerId.trim().toUpperCase();
        this.roomId = roomId.trim().toUpperCase();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Interview Session ID cannot be null or blank.");
        }
    }

    public static void validateApplicationId(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or blank.");
        }
    }

    public static void validateInterviewerId(String interviewerId) {
        if (interviewerId == null || interviewerId.trim().isEmpty()) {
            throw new ValidationException("Interviewer ID cannot be null or blank.");
        }
    }

    public static void validateRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ValidationException("Room ID cannot be null or blank.");
        }
    }

    public static void validateTimeInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ValidationException("Start time and end time cannot be null.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("Interview end time must be strictly after start time. Start: " + start + ", End: " + end);
        }
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        if (otherStart == null || otherEnd == null) return false;
        return this.startTime.isBefore(otherEnd) && otherStart.isBefore(this.endTime);
    }

    public long getDurationMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getInterviewerId() {
        return interviewerId;
    }

    public void setInterviewerId(String interviewerId) {
        validateInterviewerId(interviewerId);
        this.interviewerId = interviewerId.trim().toUpperCase();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        validateRoomId(roomId);
        this.roomId = roomId.trim().toUpperCase();
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        validateTimeInterval(startTime, this.endTime);
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        validateTimeInterval(this.startTime, endTime);
        this.endTime = endTime;
    }

    public void setTimeInterval(LocalDateTime start, LocalDateTime end) {
        validateTimeInterval(start, end);
        this.startTime = start;
        this.endTime = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InterviewSession session)) return false;
        return Objects.equals(id, session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InterviewSession{" +
                "id='" + id + '\'' +
                ", applicationId='" + applicationId + '\'' +
                ", interviewerId='" + interviewerId + '\'' +
                ", roomId='" + roomId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
