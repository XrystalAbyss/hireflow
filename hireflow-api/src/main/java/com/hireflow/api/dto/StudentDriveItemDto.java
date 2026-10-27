package com.hireflow.api.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable Data Transfer Object representing a recruitment drive with student-specific eligibility determination.
 */
public record StudentDriveItemDto(
        String id,
        String companyId,
        String companyName,
        String jobTitle,
        double minimumCgpa,
        Set<String> eligibleBranches,
        LocalDate applicationDeadline,
        boolean closed,
        boolean deadlinePassed,
        boolean hasApplied,
        String existingApplicationId,
        String existingApplicationStatus,
        boolean academicallyEligible,
        boolean acceptingApplications,
        boolean canApply,
        List<String> eligibilityReasons
) {
    public StudentDriveItemDto {
        eligibleBranches = eligibleBranches != null ? Collections.unmodifiableSet(eligibleBranches) : Collections.emptySet();
        eligibilityReasons = eligibilityReasons != null ? Collections.unmodifiableList(eligibilityReasons) : Collections.emptyList();
    }
}
