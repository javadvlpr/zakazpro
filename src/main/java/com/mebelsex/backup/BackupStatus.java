package com.mebelsex.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Current backup state for the UI. ageDays is null when no backup has ever succeeded.
 */
public record BackupStatus(LocalDateTime lastSuccess, Long ageDays, Path folder) {

    public boolean neverRun() {
        return lastSuccess == null;
    }
}
