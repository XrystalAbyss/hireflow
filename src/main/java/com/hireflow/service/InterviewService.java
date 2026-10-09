package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.SchedulingConflictException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.InterviewSession;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.scheduler.ConflictChecker;
import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service orchestrating interview session persistence, schedule publication,
 * duplicate appointment prevention, and confirmed appointment conflict revalidation.
 */
public class InterviewService {

    /**
     * Immutable result holding schedule publication statistics and conflict diagnostics.
     */
    public record PublicationResult(
            int publishedCount,
            List<InterviewSession> publishedSessions,
            List<String> skippedDuplicateApplicationIds,
            List<String> conflictRejections
    ) {
        public boolean hasConflicts() {
            return !conflictRejections.isEmpty();
        }

        public String getSummary() {
            return String.format("Published: %d, Skipped Duplicates: %d, Conflicts: %d",
                    publishedCount, skippedDuplicateApplicationIds.size(), conflictRejections.size());
        }
    }

    private final InterviewSessionRepository sessionRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final ConflictChecker conflictChecker;
    private final List<HireFlowEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public InterviewService(InterviewSessionRepository sessionRepository,
                            ApplicationRepository applicationRepository,
                            ApplicationService applicationService) {
        this(sessionRepository, applicationRepository, applicationService, new ConflictChecker());
    }

    public InterviewService(InterviewSessionRepository sessionRepository,
                            ApplicationRepository applicationRepository,
                            ApplicationService applicationService,
                            ConflictChecker conflictChecker) {
        if (sessionRepository == null || applicationRepository == null || applicationService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null.");
        }
        this.sessionRepository = sessionRepository;
        this.applicationRepository = applicationRepository;
        this.applicationService = applicationService;
        this.conflictChecker = Objects.requireNonNull(conflictChecker, "ConflictChecker cannot be null.");
    }

    public void addEventListener(HireFlowEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(HireFlowEventListener listener) {
        eventListeners.remove(listener);
    }

    private void publishEvent(HireFlowEventType type, String sourceId, String message) {
        HireFlowEvent event = new HireFlowEvent(type, sourceId, message);
        for (HireFlowEventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Publishes a proposed schedule into confirmed appointments.
     * Enforces:
     * 1. Status consistency (application updated to SCHEDULED).
     * 2. Duplicate appointment prevention (cannot publish same application twice).
     * 3. Conflict revalidation against existing confirmed appointments at publication time.
     */
    public synchronized PublicationResult publishSchedule(List<InterviewSession> proposedSessions, Duration bufferDuration) {
        if (proposedSessions == null || proposedSessions.isEmpty()) {
            return new PublicationResult(0, List.of(), List.of(), List.of());
        }

        List<InterviewSession> published = new ArrayList<>();
        List<String> skippedDuplicates = new ArrayList<>();
        List<String> conflictRejections = new ArrayList<>();

        // Active confirmed appointments pool that starts with current repository sessions
        List<InterviewSession> currentConfirmedPool = new ArrayList<>(sessionRepository.findAll());

        // Pre-build application-to-student lookup
        Map<String, String> appToStudentMap = new HashMap<>();
        for (Application app : applicationRepository.findAll()) {
            appToStudentMap.put(app.getId().toUpperCase(), app.getStudentId().toUpperCase());
        }

        for (InterviewSession session : proposedSessions) {
            String appId = session.getApplicationId().toUpperCase();

            // Requirement 4: Verify publishing the same schedule twice cannot create duplicate appointments
            List<InterviewSession> existingForApp = sessionRepository.findByApplicationId(appId);
            if (!existingForApp.isEmpty()) {
                skippedDuplicates.add(appId);
                continue;
            }
            if (sessionRepository.existsById(session.getId())) {
                Optional<InterviewSession> existingSession = sessionRepository.findById(session.getId());
                if (existingSession.isPresent() && !existingSession.get().getApplicationId().equalsIgnoreCase(appId)) {
                    conflictRejections.add(String.format("Session ID '%s' is already assigned to application '%s'.",
                            session.getId(), existingSession.get().getApplicationId()));
                } else {
                    skippedDuplicates.add(appId);
                }
                continue;
            }

            // Check if application exists
            Optional<Application> appOpt = applicationRepository.findById(appId);
            if (appOpt.isEmpty()) {
                conflictRejections.add(String.format("Application '%s' no longer exists in repository.", appId));
                continue;
            }
            Application app = appOpt.get();

            // Only shortlisted applications can be confirmed into schedule
            if (app.getStatus() != ApplicationStatus.SHORTLISTED) {
                conflictRejections.add(String.format("Application '%s' has status '%s', expected SHORTLISTED.", appId, app.getStatus()));
                continue;
            }

            String studentId = app.getStudentId();

            // Requirement 5: Revalidate conflicts against existing confirmed sessions at publication time
            if (conflictChecker.hasStudentConflict(studentId, session.getStartTime(), session.getEndTime(), currentConfirmedPool, appToStudentMap)) {
                conflictRejections.add(String.format("Student conflict: Student '%s' has an overlapping appointment during %s - %s.",
                        studentId, session.getStartTime().toLocalTime(), session.getEndTime().toLocalTime()));
                continue;
            }

            if (conflictChecker.hasInterviewerConflict(session.getInterviewerId(), session.getStartTime(), session.getEndTime(), bufferDuration, currentConfirmedPool)) {
                conflictRejections.add(String.format("Interviewer conflict: Interviewer '%s' has an overlapping appointment during %s - %s.",
                        session.getInterviewerId(), session.getStartTime().toLocalTime(), session.getEndTime().toLocalTime()));
                continue;
            }

            if (conflictChecker.hasRoomConflict(session.getRoomId(), session.getStartTime(), session.getEndTime(), bufferDuration, currentConfirmedPool)) {
                conflictRejections.add(String.format("Room conflict: Room '%s' is occupied during %s - %s.",
                        session.getRoomId(), session.getStartTime().toLocalTime(), session.getEndTime().toLocalTime()));
                continue;
            }

            // Requirement 3: Update application status consistently with session persistence (atomic with rollback)
            try {
                sessionRepository.add(session);
                try {
                    applicationService.updateStatus(session.getApplicationId(), ApplicationStatus.SCHEDULED,
                            "Interview confirmed for " + session.getStartTime().toLocalTime() + " - " + session.getEndTime().toLocalTime());
                } catch (Exception updateEx) {
                    sessionRepository.deleteById(session.getId());
                    throw updateEx;
                }
                currentConfirmedPool.add(session);
                published.add(session);
                publishEvent(HireFlowEventType.INTERVIEW_SCHEDULED, session.getId(),
                        "Interview appointment confirmed for " + studentId);
            } catch (Exception ex) {
                conflictRejections.add(String.format("Failed to publish appointment for application '%s': %s", appId, ex.getMessage()));
            }
        }

        return new PublicationResult(
                published.size(),
                Collections.unmodifiableList(published),
                Collections.unmodifiableList(skippedDuplicates),
                Collections.unmodifiableList(conflictRejections)
        );
    }

    public List<InterviewSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    public int getTotalSessionsCount() {
        return sessionRepository.count();
    }
}
