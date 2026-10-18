package com.hireflow.api.dto.officer;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Set;

public record OfficerDriveUpdateDto(
        @NotBlank(message = "Job title is required.")
        @Size(max = 100, message = "Job title cannot exceed 100 characters.")
        String jobTitle,

        @NotNull(message = "Minimum CGPA is required.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Minimum CGPA cannot be negative.")
        @DecimalMax(value = "10.0", inclusive = true, message = "Minimum CGPA cannot exceed 10.0.")
        Double minimumCgpa,

        @NotEmpty(message = "Eligible branches must contain at least one branch.")
        Set<@NotBlank(message = "Branch code cannot be blank.") String> eligibleBranches,

        @NotNull(message = "Application deadline is required.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDeadline
) {}
