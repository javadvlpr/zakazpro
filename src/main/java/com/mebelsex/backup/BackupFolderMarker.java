package com.mebelsex.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Plain-text record of the current backup folder, written next to the app data.
 * The recovery dialog reads it because the settings table inside a damaged DB is
 * not readable. Best effort: a failure never breaks a backup.
 */
public final class BackupFolderMarker {

    public static final String FILE_NAME = "backup-folder.txt";

    private BackupFolderMarker() {
    }

    /** Writes the folder path; returns true on success. */
    public static boolean write(Path appDataDir, Path folder) {
        if (appDataDir == null || folder == null) {
            return false;
        }
        try {
            Files.createDirectories(appDataDir);
            Files.writeString(appDataDir.resolve(FILE_NAME), folder.toAbsolutePath().toString(),
                    StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Reads the recorded folder, or null when there is none / it is blank. */
    public static Path read(Path appDataDir) {
        if (appDataDir == null) {
            return null;
        }
        Path marker = appDataDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            String text = Files.readString(marker, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : Paths.get(text);
        } catch (IOException e) {
            return null;
        }
    }

    /** Default backup folder when no marker exists: {@code <home>/Documents/ZakazPro/Backup}. */
    public static Path defaultFolder() {
        return Paths.get(System.getProperty("user.home"), "Documents",
                com.mebelsex.common.AppInfo.DOCUMENTS_DIR_NAME, "Backup");
    }
}
