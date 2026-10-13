package com.mebelsex.backup;

import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-backup-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class BackupServiceIntegrationTest {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private BackupService backupService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    private void useFolder(Path folder, int keepDays) {
        settingsService.saveBackup(folder.toString(), true, "00:00", true, keepDays);
    }

    @Test
    void createsBackupInDayFolderWithExpectedName() {
        useFolder(tempDir, 60);
        BackupResult result = backupService.createBackup(BackupReason.MANUAL);

        Path expectedDir = tempDir.resolve(LocalDate.now().format(DAY));
        assertEquals(expectedDir, result.path().getParent());
        assertTrue(result.path().getFileName().toString()
                .matches("zakazpro_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.db"), result.path().toString());
        assertTrue(result.sizeBytes() > 0);
        assertNotNull(backupService.status().lastSuccess());
        assertTrue(Files.exists(result.path()));
    }

    @Test
    void backupHasSameCountsAndPassesIntegrityCheck() throws Exception {
        useFolder(tempDir, 60);
        customerService.create("Backup Mijozi", null, null, null);
        long liveCustomers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class);

        BackupResult result = backupService.createBackup(BackupReason.MANUAL);

        try (Connection connection = openReadOnly(result.path());
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                assertTrue(rs.next());
                assertEquals("ok", rs.getString(1));
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM customers")) {
                assertTrue(rs.next());
                assertEquals(liveCustomers, rs.getLong(1));
            }
        }
    }

    @Test
    void folderNameWithApostropheIsEscaped() {
        Path folder = tempDir.resolve("back'up");
        useFolder(folder, 60);
        BackupResult result = backupService.createBackup(BackupReason.MANUAL);
        assertTrue(Files.exists(result.path()));
        assertTrue(result.path().toString().contains("back'up"));
    }

    @Test
    void retentionDeletesOnlyOldDatedFolders() throws Exception {
        Path oldFolder = tempDir.resolve("2020-01-01");
        Files.createDirectories(oldFolder);
        Files.writeString(oldFolder.resolve("mebel_2020-01-01_10-00.db"), "old");
        Path recentFolder = tempDir.resolve(LocalDate.now().minusDays(30).format(DAY));
        Files.createDirectories(recentFolder);
        Files.writeString(recentFolder.resolve("mebel.db"), "recent");
        Path safety = tempDir.resolve("Tiklashdan_oldin");
        Files.createDirectories(safety);
        Files.writeString(safety.resolve("mebel_oldin_x.db"), "safety");
        Files.writeString(tempDir.resolve("notes.txt"), "unrelated");

        useFolder(tempDir, 60);
        backupService.createBackup(BackupReason.MANUAL);

        assertFalse(Files.exists(oldFolder), "old dated folder should be deleted");
        assertTrue(Files.exists(recentFolder), "folder within keep_days must stay");
        assertTrue(Files.exists(safety), "Tiklashdan_oldin must never be deleted");
        assertTrue(Files.exists(tempDir.resolve("notes.txt")), "unrelated files must stay");
    }

    @Test
    void keepDaysZeroKeepsAllFolders() throws Exception {
        Path oldFolder = tempDir.resolve("2020-01-01");
        Files.createDirectories(oldFolder);
        Files.writeString(oldFolder.resolve("mebel_2020-01-01_10-00.db"), "old");

        useFolder(tempDir, 0);
        backupService.createBackup(BackupReason.MANUAL);

        assertTrue(Files.exists(oldFolder), "keep_days=0 keeps every folder");
    }

    @Test
    void unwritableFolderThrowsAndLeavesNoTmpFiles() throws Exception {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "i am a file");
        useFolder(blocked, 60);

        assertThrows(BusinessException.class, () -> backupService.createBackup(BackupReason.MANUAL));
        assertEquals(0, countTmpFiles(tempDir), "no .tmp files may remain after a failure");
    }

    @Test
    void successfulBackupLeavesNoTmpFiles() throws Exception {
        useFolder(tempDir, 60);
        backupService.createBackup(BackupReason.MANUAL);
        assertEquals(0, countTmpFiles(tempDir));
    }

    @Test
    void listRecentReturnsNewestFirstAndToleratesSameMinuteSuffix() {
        useFolder(tempDir, 60);
        backupService.createBackup(BackupReason.MANUAL);
        backupService.createBackup(BackupReason.MANUAL);

        List<BackupFileRow> rows = backupService.listRecent(15);
        assertEquals(2, rows.size(), "both backups should be listed");
        assertTrue(!rows.get(0).dateTime().isBefore(rows.get(1).dateTime()));
    }

    @Test
    void listRecentRecognisesLegacyAndNewBackupPrefixes() throws Exception {
        Path day = tempDir.resolve(LocalDate.now().format(DAY));
        Files.createDirectories(day);
        Files.writeString(day.resolve("mebel_2026-10-01_10-00.db"), "old");
        Files.writeString(day.resolve("zakazpro_2026-10-01_11-00.db"), "new");
        Files.writeString(day.resolve("notes.txt"), "unrelated");

        useFolder(tempDir, 60);
        List<BackupFileRow> rows = backupService.listRecent(15);

        assertEquals(2, rows.size(), "both the legacy and the new backup file must be listed");
        assertTrue(rows.stream().anyMatch(r -> r.path().getFileName().toString().startsWith("mebel_")),
                "old mebel_*.db backups must still be listed");
        assertTrue(rows.stream().anyMatch(r -> r.path().getFileName().toString().startsWith("zakazpro_")),
                "new zakazpro_*.db backups must be listed");
    }

    @Test
    void dataChangedSinceIgnoresSettingsOnlyChanges() {
        useFolder(tempDir, 60);
        LocalDateTime marker = LocalDateTime.now();
        settingsService.saveAttemptError("settings only");
        assertFalse(backupService.dataChangedSince(marker), "settings changes must be ignored");

        customerService.create("Changed Mijozi", null, null, null);
        assertTrue(backupService.dataChangedSince(marker), "a new customer is a data change");
    }

    // ------------------------------------------------------------------

    private static Connection openReadOnly(Path file) throws Exception {
        return DriverManager.getConnection(
                "jdbc:sqlite:file:" + file.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro");
    }

    private static long countTmpFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".tmp"))
                    .count();
        }
    }
}
