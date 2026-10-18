package com.hireflow.api.dto.scheduling;

import java.time.LocalDateTime;

public record OfficerInterviewDetailDto(
        String sessionId,
        String applicationId,
        String studentId,
        String studentName,
        String studentBranch,
        String driveId,
        String jobTitle,
        String companyId,
        String companyName,
        String interviewerId,
        String interviewerName,
        String roomId,
        String roomName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {}
