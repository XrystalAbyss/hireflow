package com.hireflow.web.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe rate limiter and brute-force throttling service for authentication endpoints.
 * <p>
 * Defense mechanisms:
 * <ul>
 *     <li>Tracks failed attempts per client IP address and per submitted username.</li>
 *     <li>Configurable failure threshold (default: 5 failures) and lockout duration (default: 5 minutes).</li>
 *     <li>Bounded in-memory cache capacity (max 5,000 entries) with automatic eviction to prevent heap exhaustion.</li>
 *     <li>Temporary duration ensures unauthenticated attackers cannot trigger permanent account denial-of-service.</li>
 *     <li>Constant feedback: Does not disclose whether a targeted username exists in the system.</li>
 * </ul>
 */
public class BruteForceThrottler {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long DEFAULT_LOCKOUT_SECONDS = 300L; // 5 minutes
    public static final int MAX_TRACKED_ENTRIES = 5000;

    private final int maxAttempts;
    private final long lockoutSeconds;

    private final Map<String, AttemptTracker> attemptsByIp = new ConcurrentHashMap<>();
    private final Map<String, AttemptTracker> attemptsByUsername = new ConcurrentHashMap<>();

    public BruteForceThrottler() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCKOUT_SECONDS);
    }

    public BruteForceThrottler(int maxAttempts, long lockoutSeconds) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockoutSeconds = Math.max(1, lockoutSeconds);
    }

    /**
     * Checks if authentication requests for the given client IP or username should be throttled.
     *
     * @param clientIp Client IP address (nullable).
     * @param username Attempted username (nullable).
     * @return true if currently locked out / throttled; false if allowed.
     */
    public boolean isBlocked(String clientIp, String username) {
        Instant now = Instant.now();
        if (clientIp != null && isTrackerBlocked(attemptsByIp.get(clientIp), now)) {
            return true;
        }
        if (username != null && isTrackerBlocked(attemptsByUsername.get(username.trim().toLowerCase()), now)) {
            return true;
        }
        return false;
    }

    /**
     * Records a failed authentication attempt.
     */
    public void recordFailure(String clientIp, String username) {
        Instant now = Instant.now();
        if (clientIp != null && !clientIp.trim().isEmpty()) {
            evictIfFull(attemptsByIp);
            attemptsByIp.compute(clientIp.trim(), (k, v) -> updateTrackerOnFailure(v, now));
        }
        if (username != null && !username.trim().isEmpty()) {
            String norm = username.trim().toLowerCase();
            evictIfFull(attemptsByUsername);
            attemptsByUsername.compute(norm, (k, v) -> updateTrackerOnFailure(v, now));
        }
    }

    /**
     * Clears tracked failure state upon successful authentication.
     */
    public void recordSuccess(String clientIp, String username) {
        if (clientIp != null) {
            attemptsByIp.remove(clientIp.trim());
        }
        if (username != null) {
            attemptsByUsername.remove(username.trim().toLowerCase());
        }
    }

    /**
     * Resets all tracking state (used in testing).
     */
    public void clear() {
        attemptsByIp.clear();
        attemptsByUsername.clear();
    }

    private boolean isTrackerBlocked(AttemptTracker tracker, Instant now) {
        if (tracker == null) {
            return false;
        }
        if (tracker.lockedUntil != null) {
            if (now.isBefore(tracker.lockedUntil)) {
                return true;
            }
            // Lockout period expired
            return false;
        }
        return false;
    }

    private AttemptTracker updateTrackerOnFailure(AttemptTracker tracker, Instant now) {
        if (tracker == null || tracker.isExpired(now, lockoutSeconds)) {
            return new AttemptTracker(1, now, null);
        }
        int newCount = tracker.failureCount + 1;
        Instant lock = (newCount >= maxAttempts) ? now.plusSeconds(lockoutSeconds) : null;
        return new AttemptTracker(newCount, now, lock);
    }

    private void evictIfFull(Map<String, AttemptTracker> map) {
        if (map.size() >= MAX_TRACKED_ENTRIES) {
            Instant now = Instant.now();
            map.entrySet().removeIf(entry -> entry.getValue().isExpired(now, lockoutSeconds));
            if (map.size() >= MAX_TRACKED_ENTRIES) {
                // If still full, prune first 500 entries
                map.keySet().stream().limit(500).forEach(map::remove);
            }
        }
    }

    private static class AttemptTracker {
        final int failureCount;
        final Instant lastFailure;
        final Instant lockedUntil;

        AttemptTracker(int failureCount, Instant lastFailure, Instant lockedUntil) {
            this.failureCount = failureCount;
            this.lastFailure = lastFailure;
            this.lockedUntil = lockedUntil;
        }

        boolean isExpired(Instant now, long windowSeconds) {
            if (lockedUntil != null) {
                return now.isAfter(lockedUntil);
            }
            return now.isAfter(lastFailure.plusSeconds(windowSeconds));
        }
    }
}
