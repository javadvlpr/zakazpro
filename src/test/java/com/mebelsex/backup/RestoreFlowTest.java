package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manually-equivalent restore flow: back up a temp DB, modify the live temp DB,
 * stage the restore and apply it on "startup"; the original data must come back
 * and the replaced copy must exist.
 */
class RestoreFlowTest {

    @TempDir
    Path dir;

    @Test
    void backupModifyScheduleApplyRestoresOriginalData() throws Exception {
        Path liveFile = dir.resolve("live.db");
        createDb(liveFile, 1);
        assertEquals(1, countRows(liveFile));

        Path backupFile = dir.resolve("mebel_backup.db");
        Files.copy(liveFile, backupFile, StandardCopyOption.REPLACE_EXISTING);

        // Simulate work done after the backup.
        insertRow(liveFile, 2);
        assertEquals(2, countRows(liveFile));

        Path dataDir = dir.resolve("appdata");
        Files.createDirectories(dataDir);
        Files.copy(liveFile, dataDir.resolve(PendingRestoreApplier.DB_NAME), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(backupFile, dataDir.resolve(PendingRestoreApplier.PENDING_NAME),
                StandardCopyOption.REPLACE_EXISTING);

        RestoreApplyResult result = PendingRestoreApplier.apply(dataDir);

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(1, countRows(dataDir.resolve(PendingRestoreApplier.DB_NAME)),
                "the original (pre-change) data must be back");

        List<Path> replaced = replacedFiles(dataDir);
        assertFalse(replaced.isEmpty(), "the replaced copy must exist");
        assertEquals(2, countRows(replaced.get(0)), "the replaced copy holds the changed data");
    }

    private static void createDb(Path file, int row) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("INSERT INTO t (id, name) VALUES (" + row + ", 'row" + row + "')");
        }
    }

    private static void insertRow(Path file, int row) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO t (id, name) VALUES (" + row + ", 'row" + row + "')");
        }
    }

    private static int countRows(Path file) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM t")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<Path> replacedFiles(Path dataDir) throws Exception {
        Path replaced = dataDir.resolve(PendingRestoreApplier.REPLACED_DIR);
        try (Stream<Path> stream = Files.list(replaced)) {
            return stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }
}
