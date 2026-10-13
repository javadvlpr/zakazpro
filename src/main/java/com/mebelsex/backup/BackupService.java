package com.mebelsex.backup;

import com.mebelsex.common.BusinessException;
import com.mebelsex.settings.AppSettings;
import com.mebelsex.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates and inspects SQLite backups. A backup is a new database file produced
 * with {@code VACUUM INTO} (never a raw file copy), written to a *.tmp file
 * first, verified, and only then renamed.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final String FILE_PREFIX = com.mebelsex.common.AppInfo.BACKUP_FILE_PREFIX;
    /** Legacy prefix kept only so old {@code mebel_*.db} backups are still recognised. */
    private static final String LEGACY_FILE_PREFIX = "mebel_";
    private static final String FILE_SUFFIX = ".db";

    private static final List<String> CORE_TABLES = List.of("flyway_schema_history", "customers", "orders", "payments");
    private static final List<String> COUNTED_TABLES = List.of("customers", "orders", "payments");

    /** Tables checked by {@link #dataChangedSince}; the settings table is deliberately excluded. */
    private static final List<String> CHANGED_TABLES = List.of(
            "customers", "orders", "payments", "employees", "positions", "operation_types",
            "work_logs", "salary_payments", "expense_categories", "expense_items", "expenses");

    private final DataSource dataSource;
    private final SettingsService settingsService;

    public BackupService(DataSource dataSource, SettingsService settingsService) {
        this.dataSource = dataSource;
        this.settingsService = settingsService;
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    public BackupResult createBackup(BackupReason reason) {
        LocalDateTime now = LocalDateTime.now();
        Path folder = settingsService.backupFolder();
        Path dayDir = folder.resolve(now.format(DAY));
        try {
            Files.createDirectories(dayDir);
        } catch (IOException e) {
            String message = "Cannot create backup folder: " + dayDir;
            settingsService.saveAttemptError(message);
            throw new BusinessException("backup.error.folder", message);
        }

        Path target = dayDir.resolve(FILE_PREFIX + now.format(STAMP) + FILE_SUFFIX);
        Path tmp = dayDir.resolve(target.getFileName().toString() + ".tmp");
        Path finalTarget = uniqueTarget(target);
        try {
            Files.deleteIfExists(tmp);
            vacuumInto(tmp);
            verify(tmp, finalTarget);
            Files.move(tmp, finalTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (BusinessException e) {
            deleteQuietly(tmp);
            settingsService.saveAttemptError(e.getMessage());
            throw e;
        } catch (IOException e) {
            deleteQuietly(tmp);
            settingsService.saveAttemptError(e.getMessage());
            throw new BusinessException("backup.error.folder", e.getMessage());
        }

        long size;
        try {
            size = Files.size(finalTarget);
        } catch (IOException e) {
            size = 0;
        }
        settingsService.saveLastSuccess(now);
        settingsService.saveAttemptError(null);
        rememberBackupFolder();
        try {
            applyRetention(folder, now.toLocalDate(), settingsService.load().backupKeepDays());
        } catch (RuntimeException e) {
            log.warn("Retention failed", e);
        }
        log.info("Backup created ({}) -> {}", reason, finalTarget);
        return new BackupResult(finalTarget, size, now);
    }

    private void vacuumInto(Path target) {
        String escaped = target.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + escaped + "'");
            }
        } catch (SQLException e) {
            throw new BusinessException("backup.error.failed", e.getMessage());
        }
    }

    private void verify(Path copy, Path finalTarget) {
        try (Connection connection = openReadOnly(copy)) {
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new BusinessException("backup.error.verify", "integrity_check != ok");
                }
            }
            Set<String> tables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : CORE_TABLES) {
                if (!tables.contains(table)) {
                    throw new BusinessException("backup.error.verify", "missing table: " + table);
                }
            }
            for (String table : COUNTED_TABLES) {
                if (count(connection, table) != countLive(table)) {
                    throw new BusinessException("backup.error.verify", "row count mismatch: " + table);
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("backup.error.verify", e.getMessage());
        }
    }

    private long countLive(String table) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new BusinessException("backup.error.verify", e.getMessage());
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection openReadOnly(Path file) throws SQLException {
        String url = "jdbc:sqlite:file:" + file.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro";
        return DriverManager.getConnection(url);
    }

    private static Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String name = target.getFileName().toString();
        String base = name.endsWith(FILE_SUFFIX) ? name.substring(0, name.length() - FILE_SUFFIX.length()) : name;
        int i = 2;
        Path candidate;
        do {
            candidate = target.resolveSibling(base + "_" + i + FILE_SUFFIX);
            i++;
        } while (Files.exists(candidate));
        return candidate;
    }

    /** Records the current backup folder next to the app data (best effort). */
    public void rememberBackupFolder() {
        BackupFolderMarker.write(appDataDir(), settingsService.backupFolder());
    }

    private Path appDataDir() {
        String property = System.getProperty("mebelsex.db.path");
        if (property != null && !property.isBlank()) {
            Path parent = Paths.get(property).toAbsolutePath().getParent();
            if (parent != null) {
                return parent;
            }
        }
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String path = url.substring("jdbc:sqlite:".length());
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            Path parent = Paths.get(path).toAbsolutePath().getParent();
            if (parent != null) {
                return parent;
            }
        } catch (SQLException e) {
            log.warn("Cannot derive app data directory", e);
        }
        return Paths.get(System.getProperty("user.home"), com.mebelsex.common.AppInfo.DATA_DIR_NAME);
    }

    // ------------------------------------------------------------------
    // retention
    // ------------------------------------------------------------------

    void applyRetention(Path folder, LocalDate today, int keepDays) {
        if (keepDays <= 0 || !Files.isDirectory(folder)) {
            return;
        }
        LocalDate cutoff = today.minusDays(keepDays);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                LocalDate day = parseDay(child.getFileName().toString());
                if (day != null && day.isBefore(cutoff)) {
                    deleteRecursively(child);
                }
            }
        } catch (IOException e) {
            log.warn("Retention scan failed for {}", folder, e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    deleteRecursively(child);
                } else {
                    Files.deleteIfExists(child);
                }
            }
        }
        Files.deleteIfExists(dir);
    }

    // ------------------------------------------------------------------
    // queries
    // ------------------------------------------------------------------

    public BackupStatus status() {
        AppSettings settings = settingsService.load();
        LocalDateTime last = settings.backupLastSuccess();
        Long age = last == null ? null : ChronoUnit.DAYS.between(last.toLocalDate(), LocalDate.now());
        return new BackupStatus(last, age, settingsService.backupFolder());
    }

    public List<BackupFileRow> listRecent(int limit) {
        Path folder = settingsService.backupFolder();
        List<BackupFileRow> rows = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return rows;
        }
        try (DirectoryStream<Path> days = Files.newDirectoryStream(folder)) {
            for (Path day : days) {
                if (!Files.isDirectory(day) || parseDay(day.getFileName().toString()) == null) {
                    continue;
                }
                // Scan by extension (not by prefix) so old mebel_*.db backups stay listed.
                try (DirectoryStream<Path> files = Files.newDirectoryStream(day, "*" + FILE_SUFFIX)) {
                    for (Path file : files) {
                        LocalDateTime time = parseStamp(file.getFileName().toString());
                        if (time == null) {
                            continue;
                        }
                        long size;
                        try {
                            size = Files.size(file);
                        } catch (IOException e) {
                            size = 0;
                        }
                        rows.add(new BackupFileRow(file, time, size));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Cannot scan backup folder {}", folder, e);
        }
        rows.sort(Comparator.comparing(BackupFileRow::dateTime).reversed());
        if (rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    /** True when any business table has a row updated after the given timestamp. */
    public boolean dataChangedSince(LocalDateTime timestamp) {
        if (timestamp == null) {
            return true;
        }
        String iso = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (Connection connection = dataSource.getConnection()) {
            for (String table : CHANGED_TABLES) {
                try (java.sql.PreparedStatement ps = connection
                        .prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE updated_at > ?")) {
                    ps.setString(1, iso);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getLong(1) > 0) {
                            return true;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("dataChangedSince failed; assuming changed", e);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static LocalDate parseDay(String name) {
        try {
            return LocalDate.parse(name, DAY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime parseStamp(String fileName) {
        String base = fileName;
        for (String prefix : new String[] { FILE_PREFIX, LEGACY_FILE_PREFIX }) {
            if (base.startsWith(prefix)) {
                base = base.substring(prefix.length());
                break;
            }
        }
        if (base.endsWith(FILE_SUFFIX)) {
            base = base.substring(0, base.length() - FILE_SUFFIX.length());
        }
        // A second backup in the same minute gets a _2, _3, ... suffix.
        base = base.replaceFirst("_\\d+$", "");
        try {
            return LocalDateTime.parse(base, STAMP);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
