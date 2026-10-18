package com.hireflow.api.dto.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OfficerSchedulingPreviewRequestDto(
        @NotBlank(message = "Drive ID is required")
        String driveId,

        @NotNull(message = "Interview date is required")
        LocalDate date,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @Min(value = 1, message = "Interview duration must be at least 1 minute")
        @Max(value = 240, message = "Interview duration cannot exceed 240 minutes")
        Integer durationMinutes,

        @Min(value = 0, message = "Buffer duration cannot be negative")
        @Max(value = 120, message = "Buffer duration cannot exceed 120 minutes")
        Integer bufferMinutes,

        List<String> interviewerIds,
        List<String> roomIds
) {
    public int resolvedDurationMinutes() {
        return durationMinutes != null ? durationMinutes : 45;
    }

    public int resolvedBufferMinutes() {
        return bufferMinutes != null ? bufferMinutes : 15;
    }
}
