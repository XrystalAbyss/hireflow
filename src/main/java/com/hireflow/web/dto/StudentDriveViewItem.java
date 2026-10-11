package com.hireflow.web.dto;

import com.hireflow.model.Application;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.service.EligibilityService;

import java.time.LocalDate;

/**
 * View-model representing a recruitment drive with student-specific eligibility determination.
 */
public record StudentDriveViewItem(
        RecruitmentDrive drive,
        String companyName,
        boolean hasApplied,
        Application existingApplication,
        EligibilityService.EligibilityResult eligibilityResult
) {
    public boolean isOpen() {
        return drive != null && drive.isOpen(LocalDate.now());
    }

    public boolean isClosed() {
        return drive != null && drive.isClosed();
    }

    public boolean isExpired() {
        return drive != null && !drive.isClosed() && drive.isDeadlinePassed(LocalDate.now());
    }

    public boolean canApply() {
        return !hasApplied && isOpen() && eligibilityResult != null && eligibilityResult.isEligible();
    }
}
