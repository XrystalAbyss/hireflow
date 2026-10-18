package com.hireflow.api.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable Data Transfer Object returning detailed eligibility evaluation for a specific recruitment drive.
 */
public record StudentDriveEligibilityDto(
        String driveId,
        String studentId,
        boolean academicallyEligible,
        boolean acceptingApplications,
        boolean alreadyApplied,
        boolean canApply,
        String existingApplicationId,
        String existingApplicationStatus,
        double minimumCgpa,
        double studentCgpa,
        Set<String> eligibleBranches,
        String studentBranch,
        LocalDate applicationDeadline,
        boolean closed,
        boolean deadlinePassed,
        List<String> reasons
) {
    public StudentDriveEligibilityDto {
        eligibleBranches = eligibleBranches != null ? Collections.unmodifiableSet(eligibleBranches) : Collections.emptySet();
        reasons = reasons != null ? Collections.unmodifiableList(reasons) : Collections.emptyList();
    }
}
