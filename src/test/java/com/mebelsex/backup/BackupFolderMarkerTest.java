package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupFolderMarkerTest {

    @TempDir
    Path tempDir;

    @Test
    void writeThenReadReturnsTheSameFolder() {
        Path appData = tempDir.resolve("appdata");
        Path folder = tempDir.resolve("backups");
        assertTrue(BackupFolderMarker.write(appData, folder));
        assertEquals(folder.toAbsolutePath(), BackupFolderMarker.read(appData));
    }

    @Test
    void readMissingMarkerReturnsNull() {
        assertNull(BackupFolderMarker.read(tempDir.resolve("appdata")));
    }

    @Test
    void blankMarkerReturnsNull() throws Exception {
        Path appData = tempDir.resolve("appdata");
        java.nio.file.Files.createDirectories(appData);
        java.nio.file.Files.writeString(appData.resolve(BackupFolderMarker.FILE_NAME), "   ");
        assertNull(BackupFolderMarker.read(appData));
    }
}
