package com.mebelsex.settings;

import java.time.LocalDateTime;

/**
 * Typed snapshot of the key/value settings table. backupTime is "HH:mm".
 */
public record AppSettings(
        String companyName,
        String companyPhone,
        String companyAddress,
        String backupFolder,
        boolean backupAutoEnabled,
        String backupTime,
        boolean backupOnClose,
        int backupKeepDays,
        LocalDateTime backupLastSuccess,
        String backupLastAttemptError) {
}
