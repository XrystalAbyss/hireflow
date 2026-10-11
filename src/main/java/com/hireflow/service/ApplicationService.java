package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;

import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service orchestrating application submission, duplicate prevention, eligibility checks,
 * and lifecycle status transitions.
 */
public class ApplicationService {

    // Valid state transitions mapping
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.of(
            ApplicationStatus.APPLIED, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.ELIGIBLE, EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
            ApplicationStatus.SHORTLISTED, EnumSet.of(ApplicationStatus.SCHEDULED, ApplicationStatus.REJECTED),
            ApplicationStatus.SCHEDULED, EnumSet.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED, ApplicationStatus.SHORTLISTED),
            ApplicationStatus.REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.SELECTED, EnumSet.noneOf(ApplicationStatus.class),
            ApplicationStatus.INELIGIBLE, EnumSet.noneOf(ApplicationStatus.class)
    );

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final EligibilityService eligibilityService;
    private final com.hireflow.repository.InterviewSessionRepository sessionRepository;
    private final AtomicInteger idCounter = new AtomicInteger(100);
    private final List<HireFlowEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public ApplicationService(ApplicationRepository applicationRepository,
                              StudentRepository studentRepository,
                              DriveRepository driveRepository,
                              EligibilityService eligibilityService) {
        this(applicationRepository, studentRepository, driveRepository, eligibilityService, null);
    }

    public ApplicationService(ApplicationRepository applicationRepository,
                              StudentRepository studentRepository,
                              DriveRepository driveRepository,
                              EligibilityService eligibilityService,
                              com.hireflow.repository.InterviewSessionRepository sessionRepository) {
        if (applicationRepository == null || studentRepository == null || driveRepository == null || eligibilityService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null.");
        }
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.driveRepository = driveRepository;
        this.eligibilityService = eligibilityService;
        this.sessionRepository = sessionRepository;
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

    public synchronized String generateNextApplicationId() {
        String id;
        do {
            id = "APP-" + idCounter.incrementAndGet();
        } while (applicationRepository.existsById(id));
        return id;
    }

    /**
     * Submits an application for a drive. Verifies student and drive exist,
     * rejects duplicates, and enforces eligibility rules.
     */
    public Application submitApplication(String studentId, String driveId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new ValidationException("Student ID is required.");
        }
        if (driveId == null || driveId.trim().isEmpty()) {
            throw new ValidationException("Drive ID is required.");
        }

        String sId = studentId.trim().toUpperCase();
        String dId = driveId.trim().toUpperCase();

        Student student = studentRepository.findById(sId)
                .orElseThrow(() -> new EntityNotFoundException("Student with ID '" + sId + "' does not exist."));

        RecruitmentDrive drive = driveRepository.findById(dId)
                .orElseThrow(() -> new EntityNotFoundException("Recruitment Drive with ID '" + dId + "' does not exist."));

        // Reject duplicate applications
        if (applicationRepository.existsByStudentAndDrive(sId, dId)) {
            throw new DuplicateEntityException(
                    "Student '" + sId + "' has already applied for Recruitment Drive '" + dId + "'."
            );
        }

        // Reject if drive is closed
        if (drive.isClosed()) {
            throw new ValidationException("Application rejected: Recruitment Drive '" + dId + "' is closed for applications.");
        }

        // Check eligibility - DO NOT BYPASS
        EligibilityService.EligibilityResult eligibility = eligibilityService.checkEligibility(student, drive);
        if (!eligibility.isEligible()) {
            throw new ValidationException("Application rejected: Student '" + sId +
                    "' is not eligible for Drive '" + dId + "'. Reasons: " + eligibility.getFormattedReason());
        }

        String appId = generateNextApplicationId();
        Application app = new Application(
                appId,
                sId,
                dId,
                ApplicationStatus.APPLIED,
                LocalDateTime.now(),
                "Application submitted and passed all academic eligibility criteria."
        );

        Application saved = applicationRepository.add(app);
        publishEvent(HireFlowEventType.APPLICATION_SUBMITTED, saved.getId(), "Application submitted for student: " + sId);
        return saved;
    }

    /**
     * Enforces valid status transitions and updates application status.
     */
    public Application updateStatus(String applicationId, ApplicationStatus newStatus, String remarks) {
        if (newStatus == null) {
            throw new ValidationException("New application status cannot be null.");
        }

        Application app = getApplicationOrThrow(applicationId);
        ApplicationStatus currentStatus = app.getStatus();

        if (currentStatus == newStatus) {
            if (remarks != null && !remarks.trim().isEmpty()) {
                app.setRemarks(remarks.trim());
                return applicationRepository.update(app);
            }
            return app;
        }

        Set<ApplicationStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(newStatus)) {
            throw new ValidationException(String.format(
                    "Invalid status transition from '%s' to '%s' for application '%s'. Permitted transitions: %s",
                    currentStatus, newStatus, applicationId, allowedTargets
            ));
        }

        app.setStatus(newStatus);
        if (remarks != null && !remarks.trim().isEmpty()) {
            app.setRemarks(remarks.trim());
        }

        Application updated = applicationRepository.update(app);
        HireFlowEventType eventType = switch (newStatus) {
            case SHORTLISTED -> HireFlowEventType.APPLICATION_SHORTLISTED;
            case SCHEDULED -> HireFlowEventType.INTERVIEW_SCHEDULED;
            case REJECTED -> HireFlowEventType.APPLICATION_REJECTED;
            case SELECTED -> HireFlowEventType.OFFER_EXTENDED;
            default -> null;
        };
        if (eventType != null) {
            publishEvent(eventType, updated.getId(), "Application transitioned to " + newStatus);
        }
        return updated;
    }

    public Application shortlistApplication(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or empty.");
        }
        String normAppId = applicationId.trim().toUpperCase();
        Application app = getApplicationOrThrow(normAppId);

        // Idempotent return if already shortlisted
        if (app.getStatus() == ApplicationStatus.SHORTLISTED) {
            return app;
        }

        // Validate state machine transition rules
        ApplicationStatus currentStatus = app.getStatus();
        Set<ApplicationStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(ApplicationStatus.SHORTLISTED)) {
            throw new ValidationException(String.format(
                    "Cannot shortlist application '%s': Invalid status transition from '%s' to 'SHORTLISTED'. Permitted transitions: %s",
                    normAppId, currentStatus, allowedTargets
            ));
        }

        // Verify linked student and drive exist
        Student student = studentRepository.findById(app.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student with ID '" + app.getStudentId() + "' not found."));
        RecruitmentDrive drive = driveRepository.findById(app.getDriveId())
                .orElseThrow(() -> new EntityNotFoundException("Recruitment Drive with ID '" + app.getDriveId() + "' not found."));

        // Recheck academic eligibility
        EligibilityService.EligibilityResult eligibility = eligibilityService.checkAcademicEligibility(student, drive);
        if (!eligibility.isEligible()) {
            throw new ValidationException("Cannot shortlist candidate: Student no longer satisfies academic eligibility criteria. Reasons: " + eligibility.getFormattedReason());
        }

        // Atomic conditional status update to prevent lost updates under concurrency
        boolean updated = applicationRepository.updateStatusConditional(
                normAppId,
                currentStatus,
                ApplicationStatus.SHORTLISTED,
                "Candidate shortlisted for technical interview round."
        );

        if (!updated) {
            // Concurrent race: check if another thread concurrently shortlisted it
            Application latest = getApplicationOrThrow(normAppId);
            if (latest.getStatus() == ApplicationStatus.SHORTLISTED) {
                return latest;
            }
            throw new ValidationException(String.format(
                    "Application '%s' was concurrently updated by another operation. Current status is '%s'.",
                    normAppId, latest.getStatus()
            ));
        }

        publishEvent(HireFlowEventType.APPLICATION_SHORTLISTED, normAppId, "Application transitioned to SHORTLISTED");
        return getApplicationOrThrow(normAppId);
    }

    public Application rejectApplication(String applicationId, String reason) {
        return updateStatus(applicationId, ApplicationStatus.REJECTED, reason != null ? reason : "Application rejected.");
    }

    public Optional<Application> getApplicationById(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) return Optional.empty();
        return applicationRepository.findById(applicationId.trim().toUpperCase());
    }

    public Application getApplicationOrThrow(String applicationId) {
        return getApplicationById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Application with ID '" + applicationId + "' not found."));
    }

    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    public List<Application> getApplicationsForDrive(String driveId) {
        if (driveId == null) return List.of();
        return applicationRepository.findByDriveId(driveId.trim().toUpperCase());
    }

    public List<Application> getApplicationsForStudent(String studentId) {
        if (studentId == null) return List.of();
        return applicationRepository.findByStudentId(studentId.trim().toUpperCase());
    }

    public List<Application> getApplicationsByStatus(ApplicationStatus status) {
        if (status == null) return List.of();
        return applicationRepository.findByStatus(status);
    }

    public List<Application> getShortlistedApplicationsForDrive(String driveId) {
        return getApplicationsForDrive(driveId).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .toList();
    }

    /**
     * Deletes an application if it is not referenced by any confirmed interview sessions
     * and is not currently in SCHEDULED status.
     */
    public boolean deleteApplication(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) return false;
        String normId = applicationId.trim().toUpperCase();

        Optional<Application> appOpt = applicationRepository.findById(normId);
        if (appOpt.isEmpty()) return false;
        Application app = appOpt.get();

        if (sessionRepository != null) {
            List<com.hireflow.model.InterviewSession> sessions = sessionRepository.findByApplicationId(normId);
            if (!sessions.isEmpty()) {
                throw new ValidationException(String.format(
                        "Cannot delete application '%s' because %d confirmed interview session(s) reference it.",
                        normId, sessions.size()
                ));
            }
        }

        if (app.getStatus() == ApplicationStatus.SCHEDULED) {
            throw new ValidationException(String.format(
                    "Cannot delete application '%s' with SCHEDULED status. Cancel interview appointment first.",
                    normId
            ));
        }

        return applicationRepository.deleteById(normId);
    }
}
