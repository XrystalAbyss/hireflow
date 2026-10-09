package com.hireflow.service.event;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable event record emitted by service operations.
 */
public record HireFlowEvent(
        LocalDateTime timestamp,
        HireFlowEventType type,
        String sourceId,
        String message
) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public HireFlowEvent(HireFlowEventType type, String sourceId, String message) {
        this(LocalDateTime.now(), type, sourceId, message);
    }

    public String toFormattedString() {
        return String.format("[%s] [%s] (%s): %s",
                timestamp.format(FORMATTER), type.name(), sourceId != null ? sourceId : "-", message);
    }
}
