package com.mebelsex.license;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects a system clock that has been moved backwards. Pure: it only compares
 * the caller-supplied timestamps and never touches the disk or the system clock.
 * A 36-hour tolerance absorbs time zone and DST changes.
 */
public final class ClockGuard {

    public enum Result {
        OK,
        ROLLED_BACK
    }

    /** Allowed backwards distance before it counts as a rollback. */
    public static final Duration TOLERANCE = Duration.ofHours(36);

    private ClockGuard() {
    }

    /**
     * @param lastSeen the highest timestamp seen before, may be null on first run
     * @param now      the current timestamp
     */
    public static Result check(Instant lastSeen, Instant now) {
        if (lastSeen == null || now == null) {
            return Result.OK;
        }
        return now.isBefore(lastSeen.minus(TOLERANCE)) ? Result.ROLLED_BACK : Result.OK;
    }

    /** The value to persist as the new "last seen" timestamp (the later of the two). */
    public static Instant nextLastSeen(Instant lastSeen, Instant now) {
        if (lastSeen == null) {
            return now;
        }
        if (now == null) {
            return lastSeen;
        }
        return now.isAfter(lastSeen) ? now : lastSeen;
    }
}
