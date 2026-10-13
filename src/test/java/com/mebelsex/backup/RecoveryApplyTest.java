package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryApplyTest {

    @TempDir
    Path tempDir;

    @Test
    void restoringReplacesDamagedDbAndKeepsItUnderMebelDamaged() throws Exception {
        Path dataDir = tempDir.resolve("appdata");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("mebel.db"), "damaged content, not sqlite");

        Path chosen = tempDir.resolve("chosen.db");
        createValidBackup(chosen, 5, 6, 7);

        Files.copy(chosen, dataDir.resolve(PendingRestoreApplier.PENDING_NAME),
                StandardCopyOption.REPLACE_EXISTING);
        RestoreApplyResult result = PendingRestoreApplier.apply(dataDir, "mebel.db", "mebel_damaged_");

        assertNotNull(result);
        assertTrue(result.success(), result.detail());

        // The chosen backup is now live and valid.
        BackupFileInspector.Inspection live = BackupFileInspector.inspect(dataDir.resolve("mebel.db"), "2");
        assertTrue(live.valid(), live.reasonKey());
        assertEquals(5, live.customers());
        assertEquals(6, live.orders());
        assertEquals(7, live.payments());

        // The damaged files were kept under replaced/mebel_damaged_*.
        List<Path> damaged = listPrefix(dataDir.resolve(PendingRestoreApplier.REPLACED_DIR), "mebel_damaged_");
        assertFalse(damaged.isEmpty(), "the damaged database must be kept");
        assertTrue(damaged.stream().anyMatch(p -> "damaged content, not sqlite".equals(readOrEmpty(p))));
    }

    @Test
    void nothingPendingLeavesTheDamagedDbUntouched() throws Exception {
        Path dataDir = tempDir.resolve("appdata");
        Files.createDirectories(dataDir);
        Path damaged = dataDir.resolve("mebel.db");
        Files.writeString(damaged, "damaged content, not sqlite");

        assertNull(PendingRestoreApplier.apply(dataDir, "mebel.db", "mebel_damaged_"));
        assertEquals("damaged content, not sqlite", Files.readString(damaged));
    }

    @Test
    void findRecentKeepsOnlyValidBackupsNewestFirst() throws Exception {
        Path searchDir = tempDir.resolve("backups");
        Path day = searchDir.resolve("2026-10-01");
        Files.createDirectories(day);

        Path older = day.resolve("mebel_2026-10-01_10-00.db");
        createValidBackup(older, 1, 1, 1);
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000_000L));

        Path newer = day.resolve("mebel_2026-10-01_11-00.db");
        createValidBackup(newer, 2, 2, 2);
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000_000L));

        Files.writeString(day.resolve("mebel_broken.db"), "not sqlite");

        List<RecoveryBackups.Candidate> candidates = RecoveryBackups.findRecent(List.of(searchDir), 5);
        assertEquals(2, candidates.size(), "only valid backups are returned");
        assertEquals(newer.toAbsolutePath(), candidates.get(0).file());
        assertEquals(2, candidates.get(0).customers());
    }

    private static List<Path> listPrefix(Path dir, String prefix) throws Exception {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
        }
    }

    private static String readOrEmpty(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            return "";
        }
    }

    private static void createValidBackup(Path file, long customers, long orders, long payments) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE flyway_schema_history (installed_rank INTEGER, version TEXT, success INTEGER)");
            statement.execute("INSERT INTO flyway_schema_history VALUES (1, '2', 1)");
            statement.execute("CREATE TABLE customers (id INTEGER)");
            statement.execute("CREATE TABLE orders (id INTEGER)");
            statement.execute("CREATE TABLE payments (id INTEGER)");
            fill(connection, "customers", customers);
            fill(connection, "orders", orders);
            fill(connection, "payments", payments);
        }
    }

    private static void fill(Connection connection, String table, long count) throws Exception {
        for (long i = 0; i < count; i++) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO " + table + " (id) VALUES (" + i + ")");
            }
        }
    }
}
