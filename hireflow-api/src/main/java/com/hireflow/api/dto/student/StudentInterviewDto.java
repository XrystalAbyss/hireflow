package com.hireflow.api.dto.student;

import java.time.LocalDateTime;

public record StudentInterviewDto(
        String sessionId,
        String applicationId,
        String studentId,
        String studentName,
        String studentBranch,
        String driveId,
        String jobTitle,
        String companyName,
        String interviewerId,
        String interviewerName,
        String roomId,
        String roomName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {}
