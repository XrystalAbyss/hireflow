package com.hireflow.scheduler;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Interviewer;
import com.hireflow.model.Room;

import java.time.Duration;
import java.util.*;

/**
 * Immutable request container providing all parameters and resource pools
 * required by the scheduling engine.
 */
public class SchedulingRequest {

    private final List<Application> applications;
    private final List<Interviewer> interviewers;
    private final Map<String, List<TimeWindow>> interviewerAvailability;
    private final List<Room> rooms;
    private final TimeWindow schedulingWindow;
    private final Duration interviewDuration;
    private final Duration bufferDuration;
    private final List<InterviewSession> existingSessions;
    private final Map<String, String> driveCompanyMap;

    private SchedulingRequest(Builder builder) {
        if (builder.schedulingWindow == null) {
            throw new ValidationException("Scheduling window must not be null.");
        }
        if (builder.interviewDuration == null || !builder.interviewDuration.isPositive()) {
            throw new ValidationException("Interview duration must be strictly positive.");
        }
        if (builder.bufferDuration != null && builder.bufferDuration.isNegative()) {
            throw new ValidationException("Buffer duration cannot be negative.");
        }

        this.applications = Collections.unmodifiableList(new ArrayList<>(builder.applications));
        this.interviewers = Collections.unmodifiableList(new ArrayList<>(builder.interviewers));
        this.rooms = Collections.unmodifiableList(new ArrayList<>(builder.rooms));
        this.schedulingWindow = builder.schedulingWindow;
        this.interviewDuration = builder.interviewDuration;
        this.bufferDuration = builder.bufferDuration != null ? builder.bufferDuration : Duration.ZERO;
        this.existingSessions = Collections.unmodifiableList(new ArrayList<>(builder.existingSessions));

        // Deep copy of interviewer availability map
        Map<String, List<TimeWindow>> copyAvail = new HashMap<>();
        if (builder.interviewerAvailability != null) {
            for (Map.Entry<String, List<TimeWindow>> entry : builder.interviewerAvailability.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copyAvail.put(entry.getKey().toUpperCase(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
                }
            }
        }
        this.interviewerAvailability = Collections.unmodifiableMap(copyAvail);

        Map<String, String> copyDriveMap = new HashMap<>();
        if (builder.driveCompanyMap != null) {
            for (Map.Entry<String, String> entry : builder.driveCompanyMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copyDriveMap.put(entry.getKey().toUpperCase(), entry.getValue().toUpperCase());
                }
            }
        }
        this.driveCompanyMap = Collections.unmodifiableMap(copyDriveMap);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Application> getApplications() {
        return applications;
    }

    public List<Interviewer> getInterviewers() {
        return interviewers;
    }

    public Map<String, List<TimeWindow>> getInterviewerAvailability() {
        return interviewerAvailability;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public TimeWindow getSchedulingWindow() {
        return schedulingWindow;
    }

    public Duration getInterviewDuration() {
        return interviewDuration;
    }

    public Duration getBufferDuration() {
        return bufferDuration;
    }

    public List<InterviewSession> getExistingSessions() {
        return existingSessions;
    }

    public Map<String, String> getDriveCompanyMap() {
        return driveCompanyMap;
    }

    public static class Builder {
        private List<Application> applications = new ArrayList<>();
        private List<Interviewer> interviewers = new ArrayList<>();
        private Map<String, List<TimeWindow>> interviewerAvailability = new HashMap<>();
        private List<Room> rooms = new ArrayList<>();
        private TimeWindow schedulingWindow;
        private Duration interviewDuration = Duration.ofMinutes(45);
        private Duration bufferDuration = Duration.ZERO;
        private List<InterviewSession> existingSessions = new ArrayList<>();
        private Map<String, String> driveCompanyMap = new HashMap<>();

        public Builder applications(List<Application> applications) {
            if (applications != null) this.applications = new ArrayList<>(applications);
            return this;
        }

        public Builder interviewers(List<Interviewer> interviewers) {
            if (interviewers != null) this.interviewers = new ArrayList<>(interviewers);
            return this;
        }

        public Builder interviewerAvailability(Map<String, List<TimeWindow>> availability) {
            if (availability != null) this.interviewerAvailability = new HashMap<>(availability);
            return this;
        }

        public Builder addInterviewerAvailability(String interviewerId, List<TimeWindow> windows) {
            if (interviewerId != null && windows != null) {
                this.interviewerAvailability.put(interviewerId.toUpperCase(), new ArrayList<>(windows));
            }
            return this;
        }

        public Builder rooms(List<Room> rooms) {
            if (rooms != null) this.rooms = new ArrayList<>(rooms);
            return this;
        }

        public Builder schedulingWindow(TimeWindow schedulingWindow) {
            this.schedulingWindow = schedulingWindow;
            return this;
        }

        public Builder interviewDuration(Duration interviewDuration) {
            this.interviewDuration = interviewDuration;
            return this;
        }

        public Builder bufferDuration(Duration bufferDuration) {
            this.bufferDuration = bufferDuration;
            return this;
        }

        public Builder existingSessions(List<InterviewSession> existingSessions) {
            if (existingSessions != null) this.existingSessions = new ArrayList<>(existingSessions);
            return this;
        }

        public Builder driveCompanyMap(Map<String, String> driveCompanyMap) {
            if (driveCompanyMap != null) this.driveCompanyMap = new HashMap<>(driveCompanyMap);
            return this;
        }

        public Builder addDriveCompany(String driveId, String companyId) {
            if (driveId != null && companyId != null) {
                this.driveCompanyMap.put(driveId.toUpperCase(), companyId.toUpperCase());
            }
            return this;
        }

        public SchedulingRequest build() {
            return new SchedulingRequest(this);
        }
    }
}
