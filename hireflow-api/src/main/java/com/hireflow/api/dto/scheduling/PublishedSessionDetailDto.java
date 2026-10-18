package com.hireflow.api.dto.scheduling;

import java.time.LocalDateTime;

public record PublishedSessionDetailDto(
        String sessionId,
        String applicationId,
        String studentId,
        String studentName,
        String interviewerName,
        String roomName,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}
