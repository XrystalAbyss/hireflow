package com.hireflow.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * Immutable Data Transfer Object summarizing student placement activity.
 */
public record StudentDashboardDto(
        StudentProfileDto profile,
        long totalEligibleDrives,
        long totalApplications,
        long shortlistedCount,
        List<StudentApplicationDetailDto> recentApplications
) {
    public StudentDashboardDto {
        recentApplications = recentApplications != null ? Collections.unmodifiableList(recentApplications) : Collections.emptyList();
    }
}
