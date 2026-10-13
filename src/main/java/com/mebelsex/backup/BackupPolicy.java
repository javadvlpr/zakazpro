package com.mebelsex.backup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Pure decision logic for automatic backups. No Spring, no I/O — easy to unit test.
 */
public final class BackupPolicy {

    private BackupPolicy() {
    }

    /** Run the startup backup when no successful backup with today's date exists. */
    public static boolean shouldRunStartup(LocalDate today, LocalDate lastSuccessDate) {
        return lastSuccessDate == null || lastSuccessDate.isBefore(today);
    }

    /**
     * Run the daily scheduled backup: only when enabled, once per day, and not
     * before the configured time.
     */
    public static boolean shouldRunScheduled(LocalDateTime now, LocalTime time, LocalDate lastScheduledDate,
            boolean enabled) {
        if (!enabled || time == null) {
            return false;
        }
        if (lastScheduledDate != null && !lastScheduledDate.isBefore(now.toLocalDate())) {
            return false;
        }
        return !now.toLocalTime().isBefore(time);
    }

    /** Run the close backup when enabled and data changed since the last backup. */
    public static boolean shouldRunOnClose(boolean enabled, boolean dataChangedSinceLastBackup) {
        return enabled && dataChangedSinceLastBackup;
    }
}
