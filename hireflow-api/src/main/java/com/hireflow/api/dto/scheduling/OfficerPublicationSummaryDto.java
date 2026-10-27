package com.hireflow.api.dto.scheduling;

import java.time.Instant;
import java.util.List;

/**
 * Structured, sanitized outcome summary for an interview timetable publication attempt.
 * <p>
 * Distinguishes full-batch confirmation ({@link PublicationOutcome#ALL_CONFIRMED}),
 * partial confirmation ({@link PublicationOutcome#PARTIAL_SUCCESS}), and total rejection ({@link PublicationOutcome#NONE_CONFIRMED}).
 */
public record OfficerPublicationSummaryDto(
        String attemptId,
        String previewId,
        String previewReference,
        String driveId,
        String jobTitle,
        String companyName,
        PublicationOutcome outcome,
        String recoveryInstructions,
        Instant publishedAt,
        int totalProposals,
        int publishedCount,
        int skippedDuplicatesCount,
        int conflictsCount,
        int errorsCount,
        int unattemptedCount,
        List<String> confirmedSessionIds,
        List<PublishedSessionDetailDto> publishedSessions,
        List<String> skippedDuplicateApplicationIds,
        List<PublicationConflictDetailDto> conflictRejections,
        List<String> infrastructureErrors
) {
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[REDACTED_PREVIEW_TOKEN]";
        }
        if (token.length() <= 8) {
            return "PREV-****";
        }
        return token.substring(0, 8) + "****";
    }
}
