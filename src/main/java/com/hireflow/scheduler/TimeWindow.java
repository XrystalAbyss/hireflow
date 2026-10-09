package com.hireflow.scheduler;

import com.hireflow.exception.ValidationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a discrete, bounded time interval [start, end].
 */
public record TimeWindow(LocalDateTime start, LocalDateTime end) implements Comparable<TimeWindow> {

    public TimeWindow {
        if (start == null || end == null) {
            throw new ValidationException("TimeWindow start and end times cannot be null.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("TimeWindow end time must be strictly after start time. Start: " + start + ", End: " + end);
        }
    }

    /**
     * Checks if this window completely encloses [targetStart, targetEnd].
     */
    public boolean contains(LocalDateTime targetStart, LocalDateTime targetEnd) {
        if (targetStart == null || targetEnd == null) return false;
        return !targetStart.isBefore(start) && !targetEnd.isAfter(end);
    }

    /**
     * Half-open interval overlap check: [s1, e1) and [s2, e2) overlap iff s1 < e2 and s2 < e1.
     * Adjacent touching intervals do NOT overlap.
     */
    public boolean overlaps(LocalDateTime targetStart, LocalDateTime targetEnd) {
        if (targetStart == null || targetEnd == null) return false;
        return start.isBefore(targetEnd) && targetStart.isBefore(end);
    }

    public boolean overlaps(TimeWindow other) {
        if (other == null) return false;
        return overlaps(other.start(), other.end());
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    @Override
    public int compareTo(TimeWindow o) {
        int comp = this.start.compareTo(o.start);
        if (comp != 0) return comp;
        return this.end.compareTo(o.end);
    }

    @Override
    public String toString() {
        return "[" + start + " -> " + end + "]";
    }
}
