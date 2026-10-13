package com.mebelsex.backup;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupPolicyTest {

    @Test
    void startupRunsWhenNoBackupToday() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        assertTrue(BackupPolicy.shouldRunStartup(today, null));
        assertTrue(BackupPolicy.shouldRunStartup(today, today.minusDays(1)));
        assertFalse(BackupPolicy.shouldRunStartup(today, today));
    }

    @Test
    void scheduledRespectsEnabledTimeAndOncePerDay() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        LocalTime time = LocalTime.of(10, 0);
        LocalDateTime beforeTime = LocalDateTime.of(today, LocalTime.of(9, 59));
        LocalDateTime afterTime = LocalDateTime.of(today, LocalTime.of(10, 5));

        assertFalse(BackupPolicy.shouldRunScheduled(afterTime, time, null, false), "disabled");
        assertFalse(BackupPolicy.shouldRunScheduled(beforeTime, time, null, true), "before the configured time");
        assertTrue(BackupPolicy.shouldRunScheduled(afterTime, time, null, true), "first run of the day");
        assertFalse(BackupPolicy.shouldRunScheduled(afterTime, time, today, true), "already ran today");
        assertTrue(BackupPolicy.shouldRunScheduled(afterTime, time, today.minusDays(1), true), "ran yesterday");
    }

    @Test
    void onCloseOnlyWhenEnabledAndDataChanged() {
        assertFalse(BackupPolicy.shouldRunOnClose(false, true));
        assertFalse(BackupPolicy.shouldRunOnClose(true, false));
        assertTrue(BackupPolicy.shouldRunOnClose(true, true));
    }
}
