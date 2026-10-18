package com.hireflow.api.dto;

import java.io.Serializable;

/**
 * Sanitized response acknowledging session termination.
 */
public record LogoutResponseDto(
        String message
) implements Serializable {}
