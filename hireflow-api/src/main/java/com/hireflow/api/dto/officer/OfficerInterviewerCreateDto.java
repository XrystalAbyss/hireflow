package com.hireflow.api.dto.officer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficerInterviewerCreateDto(
        @NotBlank(message = "Interviewer ID is required.")
        @Size(max = 20, message = "Interviewer ID cannot exceed 20 characters.")
        String id,

        @NotBlank(message = "Interviewer name is required.")
        @Size(max = 100, message = "Interviewer name cannot exceed 100 characters.")
        String name,

        @NotBlank(message = "Company ID is required.")
        @Size(max = 20, message = "Company ID cannot exceed 20 characters.")
        String companyId
) {}
