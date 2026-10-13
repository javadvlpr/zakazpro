package com.mebelsex.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;

/** Result of a successful backup. */
public record BackupResult(Path path, long sizeBytes, LocalDateTime time) {
}
