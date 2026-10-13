package com.mebelsex.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pure Java (no Spring, no JavaFX) check run by the Launcher before Spring/Flyway
 * touches the database. A missing or zero-byte file is a first run; a locked DB is
 * not treated as damage (normal error handling takes over).
 */
public final class StartupDatabaseCheck {

    public enum Status {
        /** File is a healthy SQLite database. */
        OK,
        /** Missing or zero bytes: first run. */
        FIRST_RUN,
        /** The file exists but is not a usable SQLite database. */
        DAMAGED,
        /** Another process holds the file; not damage. */
        LOCKED
    }

    private StartupDatabaseCheck() {
    }

    public static Status check(Path dbFile) {
        if (dbFile == null || !Files.exists(dbFile)) {
            return Status.FIRST_RUN;
        }
        long size;
        try {
            size = Files.size(dbFile);
        } catch (IOException e) {
            return Status.DAMAGED;
        }
        if (size == 0) {
            return Status.FIRST_RUN;
        }
        if (!BackupFileInspector.hasSqliteHeader(dbFile)) {
            return Status.DAMAGED;
        }
        try (Connection connection = BackupFileInspector.openReadOnly(dbFile)) {
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("PRAGMA quick_check")) {
                if (rs.next() && "ok".equalsIgnoreCase(rs.getString(1))) {
                    return Status.OK;
                }
                return Status.DAMAGED;
            }
        } catch (SQLException e) {
            if (isLock(e)) {
                return Status.LOCKED;
            }
            return Status.DAMAGED;
        }
    }

    private static boolean isLock(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (message.contains("locked") || message.contains("busy")) {
                return true;
            }
        }
        return false;
    }
}
