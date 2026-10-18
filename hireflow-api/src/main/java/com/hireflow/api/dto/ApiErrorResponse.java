package com.hireflow.api.dto;

import java.time.Instant;

/**
 * Standardized, safe error response envelope.
 * <p>
 * Guarantees zero leakage of database credentials, server hostnames, or internal stack traces.
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(
                status,
                error,
                message != null ? message : "An error occurred processing the request.",
                path,
                Instant.now().toString()
        );
    }
}
