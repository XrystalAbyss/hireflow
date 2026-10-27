package com.hireflow.api.dto.scheduling;

/**
 * Explicit publication outcome classification distinguishing full, partial, and zero-success timetable publication attempts.
 */
public enum PublicationOutcome {
    /**
     * All proposed interview appointments were successfully confirmed and persisted in PostgreSQL.
     */
    ALL_CONFIRMED,

    /**
     * Some appointments committed successfully while others encountered resource conflicts or stale application states.
     * Committed appointments remain active in PostgreSQL. Unconfirmed candidates remain in SHORTLISTED status.
     */
    PARTIAL_SUCCESS,

    /**
     * Zero appointments were confirmed. All proposed appointments encountered conflicts or errors.
     * Zero database mutations occurred.
     */
    NONE_CONFIRMED
}
