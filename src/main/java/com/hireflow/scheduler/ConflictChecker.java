package com.hireflow.scheduler;

import com.hireflow.model.InterviewSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reusable conflict detection utility verifying overlapping intervals,
 * resource double-booking, and availability boundaries.
 */
public class ConflictChecker {

    /**
     * Checks if two time intervals [s1, e1) and [s2, e2) overlap.
     * Touching boundaries (s1 == e2 or s2 == e1) do not overlap.
     */
    public boolean intervalsOverlap(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        if (s1 == null || e1 == null || s2 == null || e2 == null) return false;
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    /**
     * Checks if two intervals overlap when an optional buffer is required after each session.
     */
    public boolean intervalsOverlapWithBuffer(LocalDateTime s1, LocalDateTime e1,
                                              LocalDateTime s2, LocalDateTime e2,
                                              Duration buffer) {
        if (buffer == null || buffer.isZero() || buffer.isNegative()) {
            return intervalsOverlap(s1, e1, s2, e2);
        }
        // Expand interval 1 by buffer
        LocalDateTime effectiveE1 = e1.plus(buffer);
        LocalDateTime effectiveE2 = e2.plus(buffer);
        return s1.isBefore(effectiveE2) && s2.isBefore(effectiveE1);
    }

    /**
     * Checks if a student already has an interview scheduled in an overlapping time slot.
     */
    public boolean hasStudentConflict(String studentId, LocalDateTime start, LocalDateTime end,
                                      Collection<InterviewSession> sessions,
                                      Map<String, String> applicationToStudentMap) {
        if (studentId == null || sessions == null) return false;

        String normStudentId = studentId.trim().toUpperCase();
        for (InterviewSession session : sessions) {
            String appStudentId = applicationToStudentMap != null ?
                    applicationToStudentMap.get(session.getApplicationId().toUpperCase()) : null;

            // If session belongs to the same student, check time overlap
            if (normStudentId.equalsIgnoreCase(appStudentId)) {
                if (intervalsOverlap(start, end, session.getStartTime(), session.getEndTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if an interviewer already has an interview scheduled in an overlapping time slot.
     */
    public boolean hasInterviewerConflict(String interviewerId, LocalDateTime start, LocalDateTime end,
                                          Duration buffer, Collection<InterviewSession> sessions) {
        if (interviewerId == null || sessions == null) return false;

        String normInterviewerId = interviewerId.trim().toUpperCase();
        for (InterviewSession session : sessions) {
            if (normInterviewerId.equalsIgnoreCase(session.getInterviewerId())) {
                if (intervalsOverlapWithBuffer(start, end, session.getStartTime(), session.getEndTime(), buffer)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a room is already occupied during an overlapping time slot.
     */
    public boolean hasRoomConflict(String roomId, LocalDateTime start, LocalDateTime end,
                                   Duration buffer, Collection<InterviewSession> sessions) {
        if (roomId == null || sessions == null) return false;

        String normRoomId = roomId.trim().toUpperCase();
        for (InterviewSession session : sessions) {
            if (normRoomId.equalsIgnoreCase(session.getRoomId())) {
                if (intervalsOverlapWithBuffer(start, end, session.getStartTime(), session.getEndTime(), buffer)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the proposed time interval fits entirely within the global scheduling window.
     */
    public boolean isWithinWindow(LocalDateTime start, LocalDateTime end, TimeWindow window) {
        if (window == null) return false;
        return window.contains(start, end);
    }

    /**
     * Checks if the proposed time interval fits within the interviewer's specific availability windows.
     * If no availability windows are configured for the interviewer, defaults to true.
     */
    public boolean isWithinInterviewerAvailability(String interviewerId, LocalDateTime start, LocalDateTime end,
                                                   Map<String, List<TimeWindow>> availabilityMap) {
        if (interviewerId == null) return false;
        if (availabilityMap == null || availabilityMap.isEmpty()) return true;

        List<TimeWindow> windows = availabilityMap.get(interviewerId.trim().toUpperCase());
        if (windows == null || windows.isEmpty()) {
            return true; // No explicit restrictions given
        }

        for (TimeWindow window : windows) {
            if (window.contains(start, end)) {
                return true;
            }
        }
        return false;
    }
}
