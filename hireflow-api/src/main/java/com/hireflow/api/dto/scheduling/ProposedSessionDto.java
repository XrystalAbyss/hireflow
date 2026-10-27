package com.hireflow.api.dto.scheduling;

import java.time.LocalDateTime;

public record ProposedSessionDto(
        String sessionId,
        String applicationId,
        String studentId,
        String studentName,
        String studentBranch,
        String interviewerId,
        String interviewerName,
        String roomId,
        String roomName,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}
