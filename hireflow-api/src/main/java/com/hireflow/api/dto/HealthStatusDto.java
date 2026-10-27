package com.hireflow.api.dto;

import java.time.Instant;

/**
 * Immutable Data Transfer Object for public service health information.
 */
public record HealthStatusDto(
        String status,
        String timestamp,
        String service,
        String databaseStatus,
        String schemaStatus
) {
    public static HealthStatusDto healthy(String databaseStatus, String schemaStatus) {
        return new HealthStatusDto(
                "UP",
                Instant.now().toString(),
                "hireflow-api",
                databaseStatus,
                schemaStatus
        );
    }
}
