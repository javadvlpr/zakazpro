package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupFileInspectorTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileIsInvalid() {
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(tempDir.resolve("nope.db"), "2");
        assertFalse(inspection.valid());
        assertEquals("restore.error.invalid", inspection.reasonKey());
    }

    @Test
    void textFileIsNotSqlite() throws Exception {
        Path file = tempDir.resolve("text.db");
        Files.writeString(file, "not sqlite at all");
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(file, "2");
        assertFalse(inspection.valid());
        assertEquals("restore.error.not_sqlite", inspection.reasonKey());
    }

    @Test
    void validBackupWithCountsIsAccepted() throws Exception {
        Path file = tempDir.resolve("backup.db");
        createDatabase(file, "2", 2, 3, 4);
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(file, "2");
        assertTrue(inspection.valid(), inspection.reasonKey());
        assertEquals("2", inspection.flywayVersion());
        assertEquals(2, inspection.customers());
        assertEquals(3, inspection.orders());
        assertEquals(4, inspection.payments());
    }

    @Test
    void newerSchemaIsRejected() throws Exception {
        Path file = tempDir.resolve("newer.db");
        createDatabase(file, "3", 0, 0, 0);
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(file, "2");
        assertFalse(inspection.valid());
        assertEquals("restore.error.newer_schema", inspection.reasonKey());
    }

    @Test
    void missingCoreTableIsRejected() throws Exception {
        Path file = tempDir.resolve("notables.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE customers (id INTEGER)");
        }
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(file, "2");
        assertFalse(inspection.valid());
        assertEquals("restore.error.missing_tables", inspection.reasonKey());
    }

    @Test
    void compareVersionsHandlesDottedNumeric() {
        assertTrue(BackupFileInspector.compareVersions("2", "1") > 0);
        assertTrue(BackupFileInspector.compareVersions("1", "2") < 0);
        assertEquals(0, BackupFileInspector.compareVersions("2", "2"));
        assertTrue(BackupFileInspector.compareVersions("2.1", "2") > 0);
        assertTrue(BackupFileInspector.compareVersions("10", "9") > 0);
    }

    private static void createDatabase(Path file, String version,
            long customers, long orders, long payments) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE flyway_schema_history (installed_rank INTEGER, version TEXT, success INTEGER)");
            statement.execute("CREATE TABLE customers (id INTEGER)");
            statement.execute("CREATE TABLE orders (id INTEGER)");
            statement.execute("CREATE TABLE payments (id INTEGER)");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO flyway_schema_history (installed_rank, version, success) VALUES (1, ?, 1)")) {
                ps.setString(1, version);
                ps.executeUpdate();
            }
            insert(connection, "customers", customers);
            insert(connection, "orders", orders);
            insert(connection, "payments", payments);
        }
    }

    private static void insert(Connection connection, String table, long count) throws Exception {
        for (long i = 0; i < count; i++) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO " + table + " (id) VALUES (" + i + ")");
            }
        }
    }
}
