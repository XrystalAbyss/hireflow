package com.hireflow.api.dto.scheduling;

public record PublicationConflictDetailDto(
        String applicationId,
        String message,
        String category
) {}
