package com.hireflow.api.dto.scheduling;

import jakarta.validation.constraints.NotBlank;

public record OfficerSchedulingPublishRequestDto(
        @NotBlank(message = "Preview ID is required")
        String previewId
) {}
