package com.hireflow.scheduler;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Interviewer;
import com.hireflow.model.Room;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Deterministic greedy interval interview scheduling engine.
 * Generates feasible proposed interview schedules under strict conflict constraints.
 */
public class GreedyScheduler implements SchedulingEngine {

    private final ConflictChecker conflictChecker;

    public GreedyScheduler() {
        this(new ConflictChecker());
    }

    public GreedyScheduler(ConflictChecker conflictChecker) {
        this.conflictChecker = Objects.requireNonNull(conflictChecker, "ConflictChecker must not be null.");
    }

    @Override
    public SchedulingResult schedule(SchedulingRequest request) {
        if (request == null) {
            throw new ValidationException("SchedulingRequest cannot be null.");
        }

        TimeWindow window = request.getSchedulingWindow();
        Duration duration = request.getInterviewDuration();
        Duration buffer = request.getBufferDuration();

        if (window == null) {
            throw new ValidationException("Scheduling window must not be null.");
        }
        if (duration == null || !duration.isPositive()) {
            throw new ValidationException("Interview duration must be strictly positive.");
        }
        if (buffer != null && buffer.isNegative()) {
            throw new ValidationException("Buffer duration cannot be negative.");
        }

        // 1. Build immutable pool of existing confirmed appointments (Hard constraint 7)
        List<InterviewSession> activeSessionsPool = new ArrayList<>(request.getExistingSessions());

        // 2. Build application-to-student lookup
        Map<String, String> appToStudentMap = new HashMap<>();
        for (Application app : request.getApplications()) {
            appToStudentMap.put(app.getId().toUpperCase(), app.getStudentId().toUpperCase());
        }
        for (InterviewSession existing : request.getExistingSessions()) {
            // If existing session's application is not in the request, preserve its student if known
            if (!appToStudentMap.containsKey(existing.getApplicationId().toUpperCase())) {
                appToStudentMap.put(existing.getApplicationId().toUpperCase(), "STUDENT-EXISTING-" + existing.getApplicationId());
            }
        }

        // 3. Deterministic sorting of inputs
        List<Application> sortedApps = new ArrayList<>(request.getApplications());
        sortedApps.sort(Comparator
                .comparing(Application::getStudentId)
                .thenComparing(Application::getDriveId)
                .thenComparing(Application::getId));

        List<Interviewer> sortedInterviewers = new ArrayList<>(request.getInterviewers());
        sortedInterviewers.sort(Comparator.comparing(Interviewer::getId));

        List<Room> sortedRooms = new ArrayList<>(request.getRooms());
        sortedRooms.sort(Comparator.comparing(Room::getId));

        // 4. Generate candidate slot start times within the scheduling window
        List<LocalDateTime> candidateStartTimes = generateCandidateStartTimes(window, duration, buffer);

        List<InterviewSession> proposedSessions = new ArrayList<>();
        List<UnscheduledApplication> unscheduled = new ArrayList<>();
        int propIdCounter = 1;

        // 5. Greedy Allocation Pass
        for (Application app : sortedApps) {
            // Hard constraint 6: Only shortlisted applications can be scheduled
            if (app.getStatus() != ApplicationStatus.SHORTLISTED) {
                unscheduled.add(new UnscheduledApplication(
                        app,
                        "Application status is not SHORTLISTED (current status: " + app.getStatus() + ")."
                ));
                continue;
            }

            // Filter interviewers matching the drive's company (if mapped)
            String driveId = app.getDriveId().toUpperCase();
            String targetCompanyId = request.getDriveCompanyMap().get(driveId);

            List<Interviewer> candidateInterviewers;
            if (targetCompanyId != null && !targetCompanyId.isEmpty()) {
                candidateInterviewers = sortedInterviewers.stream()
                        .filter(i -> i.getCompanyId().equalsIgnoreCase(targetCompanyId))
                        .toList();
                if (candidateInterviewers.isEmpty()) {
                    unscheduled.add(new UnscheduledApplication(
                            app,
                            "No interviewers available for company '" + targetCompanyId + "' associated with drive '" + driveId + "'."
                    ));
                    continue;
                }
            } else {
                candidateInterviewers = sortedInterviewers;
                if (candidateInterviewers.isEmpty()) {
                    unscheduled.add(new UnscheduledApplication(app, "No interviewers available in resource pool."));
                    continue;
                }
            }

            // Check if rooms are available
            if (sortedRooms.isEmpty()) {
                unscheduled.add(new UnscheduledApplication(app, "No interview rooms available."));
                continue;
            }

            // Check if candidate start times exist
            if (candidateStartTimes.isEmpty()) {
                unscheduled.add(new UnscheduledApplication(
                        app,
                        "Interview duration (" + duration.toMinutes() + " mins) exceeds the scheduling window " + window + "."
                ));
                continue;
            }

            boolean scheduled = false;
            boolean studentHadConflict = false;
            boolean interviewerHadConflict = false;
            boolean roomHadConflict = false;
            boolean outsideInterviewerAvailability = false;

            // Search earliest feasible slot, interviewer, and room
            for (LocalDateTime slotStart : candidateStartTimes) {
                LocalDateTime slotEnd = slotStart.plus(duration);

                // Hard constraint 4: Must fit entirely within window
                if (!conflictChecker.isWithinWindow(slotStart, slotEnd, window)) {
                    continue;
                }

                // Hard constraint 1: Student cannot attend overlapping interviews
                if (conflictChecker.hasStudentConflict(app.getStudentId(), slotStart, slotEnd, activeSessionsPool, appToStudentMap)) {
                    studentHadConflict = true;
                    continue;
                }

                // Try each available interviewer
                for (Interviewer interviewer : candidateInterviewers) {
                    // Hard constraint 4: Interviewer availability window
                    if (!conflictChecker.isWithinInterviewerAvailability(interviewer.getId(), slotStart, slotEnd, request.getInterviewerAvailability())) {
                        outsideInterviewerAvailability = true;
                        continue;
                    }

                    // Hard constraint 2: Interviewer cannot conduct overlapping interviews (including buffer)
                    if (conflictChecker.hasInterviewerConflict(interviewer.getId(), slotStart, slotEnd, buffer, activeSessionsPool)) {
                        interviewerHadConflict = true;
                        continue;
                    }

                    // Try each room
                    for (Room room : sortedRooms) {
                        // Hard constraint 3: Room cannot host overlapping sessions (including buffer)
                        if (conflictChecker.hasRoomConflict(room.getId(), slotStart, slotEnd, buffer, activeSessionsPool)) {
                            roomHadConflict = true;
                            continue;
                        }

                        // Feasible slot found!
                        String sessionId = String.format("SES-PROP-%03d", propIdCounter++);
                        InterviewSession proposedSession = new InterviewSession(
                                sessionId,
                                app.getId(),
                                interviewer.getId(),
                                room.getId(),
                                slotStart,
                                slotEnd
                        );

                        // Add to active pool and proposed list
                        activeSessionsPool.add(proposedSession);
                        proposedSessions.add(proposedSession);
                        scheduled = true;
                        break;
                    }

                    if (scheduled) break;
                }

                if (scheduled) break;
            }

            if (!scheduled) {
                // Formulate descriptive rejection reason
                String reason;
                if (outsideInterviewerAvailability && !interviewerHadConflict && !roomHadConflict) {
                    reason = "Interviewer not available during the allowed scheduling window.";
                } else if (studentHadConflict) {
                    reason = "Student has overlapping interview appointments in all available time slots.";
                } else if (roomHadConflict) {
                    reason = "Insufficient room capacity; all interview rooms occupied during feasible windows.";
                } else {
                    reason = "Insufficient scheduling capacity; all slots occupied or conflicting across available resources.";
                }
                unscheduled.add(new UnscheduledApplication(app, reason));
            }
        }

        return new SchedulingResult(proposedSessions, unscheduled, request.getApplications().size());
    }

    /**
     * Generates discrete candidate start times across the scheduling window.
     */
    private List<LocalDateTime> generateCandidateStartTimes(TimeWindow window, Duration duration, Duration buffer) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime current = window.start();
        LocalDateTime windowEnd = window.end();

        // Step by 15-minute increments (or duration if less than 15 mins) for flexible slot alignment
        long stepMinutes = Math.min(15, duration.toMinutes());
        if (stepMinutes <= 0) stepMinutes = 15;
        Duration step = Duration.ofMinutes(stepMinutes);

        while (!current.plus(duration).isAfter(windowEnd)) {
            slots.add(current);
            current = current.plus(step);
        }

        return Collections.unmodifiableList(slots);
    }
}
