package com.hireflow.network.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable log record capturing a single networking event, socket connection lifecycle change,
 * or public announcement broadcast.
 * <p>
 * Strictly sanitizes data to guarantee candidate privacy: never contains student names,
 * identifiers, application scores, or interview appointments.
 */
public record NetworkActivity(
        LocalDateTime timestamp,
        String action,
        String details,
        String status
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String formattedTime() {
        return timestamp != null ? timestamp.format(TIME_FORMATTER) : "--:--:--";
    }
}
