package com.hireflow.api.dto.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OfficerSchedulingPreviewResponseDto(
        String previewId,
        String driveId,
        String jobTitle,
        String companyName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        int bufferMinutes,
        Instant expiresAt,
        int totalRequested,
        int totalScheduled,
        int totalUnscheduled,
        List<ProposedSessionDto> proposedSessions,
        List<UnscheduledCandidateDto> unscheduledCandidates
) {}
