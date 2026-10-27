package com.hireflow.api.dto.officer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficerCompanyCreateDto(
        @NotBlank(message = "Company ID is required.")
        @Size(max = 20, message = "Company ID cannot exceed 20 characters.")
        String id,

        @NotBlank(message = "Company name is required.")
        @Size(max = 100, message = "Company name cannot exceed 100 characters.")
        String name,

        @NotBlank(message = "Industry is required.")
        @Size(max = 100, message = "Industry cannot exceed 100 characters.")
        String industry
) {}
