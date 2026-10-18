package com.hireflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Validated credentials payload for REST authentication.
 */
public record LoginRequestDto(
        @NotBlank(message = "Username cannot be blank")
        @Size(min = 3, max = 50, message = "Username length must be between 3 and 50 characters")
        String username,

        @NotBlank(message = "Password cannot be blank")
        @Size(max = 128, message = "Password cannot exceed 128 characters")
        String password
) {}
