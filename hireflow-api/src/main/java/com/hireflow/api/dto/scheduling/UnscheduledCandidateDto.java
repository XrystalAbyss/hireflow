package com.hireflow.api.dto.scheduling;

public record UnscheduledCandidateDto(
        String applicationId,
        String studentId,
        String studentName,
        String studentBranch,
        String reason
) {}
