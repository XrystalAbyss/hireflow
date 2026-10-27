package com.hireflow.web.dto;

import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;

import java.time.format.DateTimeFormatter;

/**
 * View-model representing an applied recruitment drive for candidate tracking.
 */
public record StudentApplicationViewItem(
        Application application,
        String companyName,
        String jobTitle,
        String formattedAppliedAt,
        String statusExplanation,
        String statusBadgeClass
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static StudentApplicationViewItem from(Application app, String companyName, String jobTitle) {
        String formattedDate = (app.getAppliedAt() != null)
                ? app.getAppliedAt().format(FORMATTER)
                : "N/A";

        ApplicationStatus status = app.getStatus();
        String badgeClass = switch (status) {
            case APPLIED -> "badge-applied";
            case SHORTLISTED -> "badge-shortlisted";
            case SCHEDULED -> "badge-scheduled";
            case SELECTED -> "badge-selected";
            case REJECTED, INELIGIBLE -> "badge-rejected";
            default -> "badge-role";
        };

        String explanation = switch (status) {
            case APPLIED -> "Application registered and eligibility confirmed. Pending placement cell review.";
            case SHORTLISTED -> "Shortlisted for interview rounds. Schedule allocation in progress.";
            case SCHEDULED -> "Interview appointment confirmed. Check your schedule timings.";
            case SELECTED -> "Congratulations! Placement offer extended by employer.";
            case REJECTED -> "Application not selected for this recruitment cycle.";
            case INELIGIBLE -> "Candidate did not satisfy minimum cutoff or departmental criteria.";
            case ELIGIBLE -> "Eligibility confirmed.";
        };

        return new StudentApplicationViewItem(app, companyName, jobTitle, formattedDate, explanation, badgeClass);
    }
}
