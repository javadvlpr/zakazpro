package com.mebelsex.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java/JDBC inspection of a candidate SQLite database file (header, integrity,
 * core tables, Flyway version, row counts). No Spring, no JavaFX, so it can be used
 * both by {@link RestoreService} and by the startup recovery path.
 */
public final class BackupFileInspector {

    public static final Set<String> CORE_TABLES = Set.of("flyway_schema_history", "customers", "orders", "payments");

    private static final byte[] SQLITE_HEADER = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);

    /** Result of inspecting a file: either valid with counts, or invalid with a reason key. */
    public record Inspection(boolean valid, String reasonKey, String flywayVersion,
            long customers, long orders, long payments) {
    }

    private BackupFileInspector() {
    }

    /**
     * Inspects a file. When {@code latestKnownVersion} is null the newer-schema check
     * is skipped (used by the recovery path, which has no Spring/Flyway).
     */
    public static Inspection inspect(Path file, String latestKnownVersion) {
        if (file == null || !Files.isRegularFile(file)) {
            return invalid("restore.error.invalid");
        }
        if (!hasSqliteHeader(file)) {
            return invalid("restore.error.not_sqlite");
        }
        try (Connection connection = openReadOnly(file)) {
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    return invalid("restore.error.corrupt");
                }
            }
            Set<String> tables = tables(connection);
            if (!tables.containsAll(CORE_TABLES)) {
                return invalid("restore.error.missing_tables");
            }
            String version = latestAppliedVersion(connection);
            if (latestKnownVersion != null && version != null
                    && compareVersions(version, latestKnownVersion) > 0) {
                return invalid("restore.error.newer_schema");
            }
            return new Inspection(true, null, version,
                    count(connection, "customers"), count(connection, "orders"),
                    count(connection, "payments"));
        } catch (SQLException e) {
            return invalid("restore.error.invalid");
        }
    }

    private static Inspection invalid(String reasonKey) {
        return new Inspection(false, reasonKey, null, 0, 0, 0);
    }

    /** True when the file starts with the SQLite magic header. */
    public static boolean hasSqliteHeader(Path file) {
        try (var in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(SQLITE_HEADER.length);
            if (header.length < SQLITE_HEADER.length) {
                return false;
            }
            for (int i = 0; i < SQLITE_HEADER.length; i++) {
                if (header[i] != SQLITE_HEADER[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Read-only JDBC connection to a file (no writes possible). */
    public static Connection openReadOnly(Path file) throws SQLException {
        String url = "jdbc:sqlite:file:" + file.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro";
        return DriverManager.getConnection(url);
    }

    public static Set<String> tables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    public static String latestAppliedVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException ignored) {
            // older backup without a history version column
        }
        return null;
    }

    public static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Compares dotted numeric versions; non-numeric parts compare as strings. */
    public static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int length = Math.max(pa.length, pb.length);
        for (int i = 0; i < length; i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(sa), Integer.parseInt(sb));
            } catch (NumberFormatException e) {
                cmp = sa.compareTo(sb);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
