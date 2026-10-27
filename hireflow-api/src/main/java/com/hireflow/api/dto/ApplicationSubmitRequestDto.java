package com.hireflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable request payload for student application submission.
 * Authoritative student identity is derived exclusively from the session principal.
 */
public record ApplicationSubmitRequestDto(
        @NotBlank(message = "Drive ID is required.")
        @Size(max = 32, message = "Drive ID cannot exceed 32 characters.")
        String driveId
) {}
