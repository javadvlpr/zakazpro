package com.mebelsex.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;

/** One existing backup file on disk. */
public record BackupFileRow(Path path, LocalDateTime dateTime, long sizeBytes) {
}
