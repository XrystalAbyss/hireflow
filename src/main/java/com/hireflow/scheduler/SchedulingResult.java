package com.hireflow.scheduler;

import com.hireflow.model.InterviewSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable container encapsulating the output of the scheduling engine.
 */
public class SchedulingResult {

    private final List<InterviewSession> scheduledSessions;
    private final List<UnscheduledApplication> unscheduledApplications;
    private final int totalRequested;

    public SchedulingResult(List<InterviewSession> scheduledSessions,
                            List<UnscheduledApplication> unscheduledApplications,
                            int totalRequested) {
        this.scheduledSessions = scheduledSessions != null ?
                Collections.unmodifiableList(new ArrayList<>(scheduledSessions)) : Collections.emptyList();
        this.unscheduledApplications = unscheduledApplications != null ?
                Collections.unmodifiableList(new ArrayList<>(unscheduledApplications)) : Collections.emptyList();
        this.totalRequested = totalRequested;
    }

    public List<InterviewSession> getScheduledSessions() {
        return scheduledSessions;
    }

    public List<UnscheduledApplication> getUnscheduledApplications() {
        return unscheduledApplications;
    }

    public int getTotalRequested() {
        return totalRequested;
    }

    public int getTotalScheduled() {
        return scheduledSessions.size();
    }

    public int getTotalUnscheduled() {
        return unscheduledApplications.size();
    }

    public boolean isFullyScheduled() {
        return unscheduledApplications.isEmpty();
    }

    public String getSummary() {
        return String.format("Scheduling Result: %d requested, %d scheduled, %d unscheduled",
                totalRequested, getTotalScheduled(), getTotalUnscheduled());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
