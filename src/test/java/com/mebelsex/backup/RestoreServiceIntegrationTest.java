package com.mebelsex.backup;

import com.mebelsex.customers.CustomerService;
import com.mebelsex.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-restore-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class RestoreServiceIntegrationTest {

    @Autowired
    private RestoreService restoreService;
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

    @AfterEach
    void cleanPending() throws IOException {
        Path appDir = restoreService.appDataDir();
        Files.deleteIfExists(appDir.resolve(PendingRestoreApplier.PENDING_NAME));
        Files.deleteIfExists(appDir.resolve(PendingRestoreApplier.PENDING_MARKER));
    }

    private BackupResult makeBackup() {
        settingsService.saveBackup(tempDir.toString(), true, "00:00", true, 60);
        return backupService.createBackup(BackupReason.MANUAL);
    }

    @Test
    void rejectsTextFileAsNotSqlite() throws Exception {
        Path text = tempDir.resolve("not-a-db.db");
        Files.writeString(text, "this is not a sqlite database");

        RestorePreview preview = restoreService.inspect(text);
        assertFalse(preview.valid());
        assertEquals("restore.error.not_sqlite", preview.reasonKey());
    }

    @Test
    void rejectsMissingFile() {
        RestorePreview preview = restoreService.inspect(tempDir.resolve("missing.db"));
        assertFalse(preview.valid());
    }

    @Test
    void rejectsTruncatedBackup() throws Exception {
        BackupResult backup = makeBackup();
        byte[] full = Files.readAllBytes(backup.path());
        byte[] truncated = java.util.Arrays.copyOf(full, 100);
        Path truncatedFile = tempDir.resolve("truncated.db");
        Files.write(truncatedFile, truncated);

        RestorePreview preview = restoreService.inspect(truncatedFile);
        assertFalse(preview.valid(), "a truncated file must be rejected");
    }

    @Test
    void rejectsNewerFlywayVersion() throws Exception {
        BackupResult backup = makeBackup();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup.path());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE flyway_schema_history SET version='99' WHERE installed_rank=(SELECT MAX(installed_rank) FROM flyway_schema_history)");
        }

        RestorePreview preview = restoreService.inspect(backup.path());
        assertFalse(preview.valid());
        assertEquals("restore.error.newer_schema", preview.reasonKey());
    }

    @Test
    void acceptsValidBackupWithCounts() {
        settingsService.saveBackup(tempDir.toString(), true, "00:00", true, 60);
        customerService.create("Restore Mijozi", null, null, null);
        long liveCustomers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class);
        BackupResult backup = backupService.createBackup(BackupReason.MANUAL);

        RestorePreview preview = restoreService.inspect(backup.path());
        assertTrue(preview.valid(), preview.reasonKey());
        assertEquals(liveCustomers, preview.customers());
        assertEquals(preview.customers(), preview.currentCustomers());
        assertNotNull(preview.schemaVersion());
    }

    @Test
    void scheduleRestoreStagesFileAndLeavesLiveDbUntouched() throws Exception {
        BackupResult backup = makeBackup();
        long liveCustomersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class);

        restoreService.scheduleRestore(backup.path());

        Path appDir = restoreService.appDataDir();
        assertTrue(Files.exists(appDir.resolve(PendingRestoreApplier.PENDING_NAME)), "pending file must exist");
        assertEquals(Files.size(backup.path()), Files.size(appDir.resolve(PendingRestoreApplier.PENDING_NAME)));
        assertEquals(liveCustomersBefore,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Long.class),
                "live DB must not change");
    }

    @Test
    void scheduleRestoreCopiesExactContent() throws Exception {
        BackupResult backup = makeBackup();
        restoreService.scheduleRestore(backup.path());

        Path appDir = restoreService.appDataDir();
        assertEquals(-1, Files.mismatch(backup.path(), appDir.resolve(PendingRestoreApplier.PENDING_NAME)),
                "pending file must equal the source backup");
    }
}
