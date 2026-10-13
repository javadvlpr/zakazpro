package com.mebelsex.backup;

import com.mebelsex.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-marker-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class BackupFolderMarkerIntegrationTest {

    @Autowired
    private BackupService backupService;
    @Autowired
    private SettingsService settingsService;

    @TempDir
    Path tempDir;

    private String previousDbPath;

    @BeforeEach
    void pointAppDataAtTempDir() {
        previousDbPath = System.getProperty("mebelsex.db.path");
        System.setProperty("mebelsex.db.path", tempDir.resolve("mebel.db").toAbsolutePath().toString());
    }

    @AfterEach
    void restoreDbPath() {
        if (previousDbPath == null) {
            System.clearProperty("mebelsex.db.path");
        } else {
            System.setProperty("mebelsex.db.path", previousDbPath);
        }
    }

    @Test
    void markerPointsAtTheBackupFolderAfterASuccessfulBackup() {
        Path folder = tempDir.resolve("backups");
        settingsService.saveBackup(folder.toString(), true, "00:00", false, 60);

        backupService.createBackup(BackupReason.MANUAL);

        Path recorded = BackupFolderMarker.read(tempDir);
        assertNotNull(recorded, "backup-folder.txt must be written after a successful backup");
        assertEquals(folder.toAbsolutePath(), recorded);
    }
}
