package com.hireflow.api.service;

import com.hireflow.api.dto.scheduling.OfficerPublicationSummaryDto;
import com.hireflow.api.exception.PreviewConsumedException;
import com.hireflow.api.exception.PreviewExpiredException;
import com.hireflow.api.exception.PreviewNotFoundException;
import com.hireflow.web.dto.SchedulingPreviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, bounded, in-memory cache service managing interview scheduling preview tokens.
 * <p>
 * Operational & Security invariants:
 * <ul>
 *     <li>Tokens are cryptographically unpredictable (128-bit hex format via {@link SchedulingPreviewState}).</li>
 *     <li>Tied to authenticated officer username to prevent cross-officer token reuse or privilege escalation.</li>
 *     <li>15-minute bounded expiration enforced deterministically.</li>
 *     <li>Single-use atomic consumption: prevents replay attacks and concurrent multi-submission races.</li>
 *     <li>Bounded memory footprint (1,000 max entries) with proactive expired-entry eviction and FIFO fallback on creation timestamp.</li>
 *     <li>Single-instance in-memory cache: instance-local and not distributed across cluster nodes without external cache synchronization.</li>
 *     <li>Diagnostic log hygiene: Raw preview tokens are masked (e.g. PREV-****) to avoid credential or token leakage in server logs.</li>
 * </ul>
 */
@Service
public class SchedulingPreviewCacheService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingPreviewCacheService.class);
    private static final int MAX_PREVIEWS = 1000;

    private final ConcurrentMap<String, SchedulingPreviewState> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OfficerPublicationSummaryDto> publicationOutcomes = new ConcurrentHashMap<>();

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[REDACTED_PREVIEW_TOKEN]";
        }
        if (token.length() <= 8) {
            return "PREV-****";
        }
        return token.substring(0, 8) + "****";
    }

    /**
     * Stores a generated scheduling preview state in the cache.
     */
    public void put(SchedulingPreviewState preview) {
        if (preview == null || preview.previewId() == null) {
            return;
        }
        evictExpired();
        if (cache.size() >= MAX_PREVIEWS) {
            evictOldest();
        }
        cache.put(preview.previewId().trim().toUpperCase(), preview);
        log.debug("Stored scheduling preview '{}' for officer '{}' (expires at {})",
                maskToken(preview.previewId()), preview.officerUsername(), preview.expiresAt());
    }

    /**
     * Retrieves an active preview state without consuming it.
     */
    public Optional<SchedulingPreviewState> get(String previewId) {
        if (previewId == null || previewId.isBlank()) {
            return Optional.empty();
        }
        String normId = previewId.trim().toUpperCase();
        SchedulingPreviewState state = cache.get(normId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.isExpired()) {
            cache.remove(normId);
            publicationOutcomes.remove(normId);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    /**
     * Atomically consumes a preview token for publication.
     * Enforces existence, officer ownership, non-expired status, and single-use invariant.
     *
     * @param previewId       the preview identifier to consume
     * @param officerUsername the authenticated officer executing the publication
     * @return the original unconsumed preview state to proceed with publication
     * @throws PreviewNotFoundException if preview does not exist
     * @throws AccessDeniedException    if preview belongs to another officer
     * @throws PreviewConsumedException if preview was already consumed (replay attempt)
     * @throws PreviewExpiredException  if preview has expired
     */
    public SchedulingPreviewState consume(String previewId, String officerUsername) {
        if (previewId == null || previewId.isBlank()) {
            throw new PreviewNotFoundException("Missing or blank preview identifier.");
        }
        String normId = previewId.trim().toUpperCase();

        AtomicReference<SchedulingPreviewState> originalRef = new AtomicReference<>();
        cache.compute(normId, (k, existing) -> {
            if (existing == null) {
                throw new PreviewNotFoundException("Scheduling preview '" + maskToken(normId) + "' was not found or has expired.");
            }
            if (officerUsername != null && !existing.officerUsername().equalsIgnoreCase(officerUsername)) {
                log.warn("[Security] Cross-officer preview publication attempt: officer '{}' attempted to publish preview '{}' owned by '{}'",
                        officerUsername, maskToken(normId), existing.officerUsername());
                throw new AccessDeniedException("Access denied: Preview belongs to another officer account.");
            }
            if (existing.isConsumed()) {
                log.warn("[Security] Scheduling preview replay blocked: token '{}' has already been published.", maskToken(normId));
                throw new PreviewConsumedException("Scheduling preview '" + maskToken(normId) + "' has already been published. Replay prevented. Consult GET /api/v1/officer/scheduling/published?driveId=" + existing.driveId() + " to inspect confirmed appointments.");
            }
            if (existing.isExpired()) {
                log.warn("[Security] Expired scheduling preview publication rejected: token '{}' expired at {}", maskToken(normId), existing.expiresAt());
                throw new PreviewExpiredException("Scheduling preview '" + maskToken(normId) + "' has expired (15-minute validity). Please generate a fresh preview.");
            }

            originalRef.set(existing);
            return existing.markConsumed();
        });

        return originalRef.get();
    }

    /**
     * Records the outcome of a timetable publication attempt for a consumed preview token.
     */
    public void recordPublicationOutcome(String previewId, OfficerPublicationSummaryDto outcome) {
        if (previewId != null && outcome != null) {
            publicationOutcomes.put(previewId.trim().toUpperCase(), outcome);
        }
    }

    /**
     * Retrieves the recorded publication outcome for a consumed preview token, if available.
     */
    public Optional<OfficerPublicationSummaryDto> getPublicationOutcome(String previewId) {
        if (previewId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(publicationOutcomes.get(previewId.trim().toUpperCase()));
    }

    /**
     * Removes expired preview entries and associated publication records from the cache.
     */
    public void evictExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                publicationOutcomes.remove(entry.getKey());
            }
            return expired;
        });
    }

    /**
     * Evicts the oldest created entry (FIFO) when cache is at maximum capacity.
     */
    private void evictOldest() {
        cache.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().createdAt()))
                .ifPresent(oldest -> {
                    cache.remove(oldest.getKey());
                    publicationOutcomes.remove(oldest.getKey());
                });
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        publicationOutcomes.clear();
    }
}
