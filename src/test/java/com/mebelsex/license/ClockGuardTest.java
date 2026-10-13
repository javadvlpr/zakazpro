package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure clock guard cases including the 36-hour tolerance. */
class ClockGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void firstRunWithoutALastSeenTimestampIsOk() {
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(null, NOW));
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, null));
    }

    @Test
    void equalTimestampsAreOk() {
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, NOW));
    }

    @Test
    void movingForwardIsOk() {
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, NOW.plus(Duration.ofDays(3))));
    }

    @Test
    void smallBackwardsStepIsTolerated() {
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, NOW.minus(Duration.ofHours(1))));
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, NOW.minus(Duration.ofHours(35))));
    }

    @Test
    void largeBackwardsStepIsARollback() {
        assertEquals(ClockGuard.Result.ROLLED_BACK, ClockGuard.check(NOW, NOW.minus(Duration.ofHours(40))));
        assertEquals(ClockGuard.Result.ROLLED_BACK, ClockGuard.check(NOW, NOW.minus(Duration.ofDays(30))));
    }

    @Test
    void toleranceBoundaryIsExclusive() {
        assertEquals(ClockGuard.Result.OK, ClockGuard.check(NOW, NOW.minus(Duration.ofHours(36))));
        assertEquals(ClockGuard.Result.ROLLED_BACK,
                ClockGuard.check(NOW, NOW.minus(Duration.ofHours(36)).minusMillis(1)));
    }

    @Test
    void nextLastSeenKeepsTheHighestTimestamp() {
        assertEquals(NOW, ClockGuard.nextLastSeen(null, NOW));
        assertEquals(NOW, ClockGuard.nextLastSeen(NOW, null));
        assertEquals(NOW.plusSeconds(1), ClockGuard.nextLastSeen(NOW, NOW.plusSeconds(1)));
        assertEquals(NOW, ClockGuard.nextLastSeen(NOW, NOW.minus(Duration.ofDays(1))));
    }
}
