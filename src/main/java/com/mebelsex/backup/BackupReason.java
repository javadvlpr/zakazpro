package com.mebelsex.backup;

/** Why a backup was taken. */
public enum BackupReason {
    MANUAL,
    SCHEDULED,
    STARTUP,
    ON_CLOSE,
    BEFORE_RESTORE
}
