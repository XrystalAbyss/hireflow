package com.hireflow.api.dto.officer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfficerRoomUpdateDto(
        @NotBlank(message = "Room name is required.")
        @Size(max = 100, message = "Room name cannot exceed 100 characters.")
        String name,

        @NotNull(message = "Room capacity is required.")
        @Min(value = 1, message = "Room capacity must be at least 1.")
        Integer capacity
) {}
