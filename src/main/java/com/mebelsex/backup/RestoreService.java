package com.mebelsex.backup;

import com.mebelsex.common.BusinessException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Validates a candidate backup file and stages it for a restore on the next
 * start. The live database is never touched while the app runs.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    private final DataSource dataSource;
    private final BackupService backupService;
    private final Flyway flyway;

    public RestoreService(DataSource dataSource, BackupService backupService, Flyway flyway) {
        this.dataSource = dataSource;
        this.backupService = backupService;
        this.flyway = flyway;
    }

    // ------------------------------------------------------------------
    // inspect
    // ------------------------------------------------------------------

    public RestorePreview inspect(Path file) {
        long currentCustomers = countLive("customers");
        long currentOrders = countLive("orders");
        long currentPayments = countLive("payments");

        // File inspection lives in the pure BackupFileInspector (shared with startup recovery).
        BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(file, latestKnownVersion());
        if (!inspection.valid()) {
            return RestorePreview.invalid(inspection.reasonKey(), currentCustomers, currentOrders, currentPayments);
        }
        return new RestorePreview(true, null, fileTime(file), inspection.flywayVersion(),
                inspection.customers(), inspection.orders(), inspection.payments(),
                currentCustomers, currentOrders, currentPayments);
    }

    // ------------------------------------------------------------------
    // schedule
    // ------------------------------------------------------------------

    /**
     * Takes a safety backup of the current DB, then stages the given file as
     * restore_pending.db. The live DB stays untouched.
     */
    public RestoreApplyResult scheduleRestore(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new BusinessException("restore.error.invalid");
        }
        backupService.createBackup(BackupReason.BEFORE_RESTORE);
        Path appDir = appDataDir();
        Path pending = appDir.resolve(PendingRestoreApplier.PENDING_NAME);
        try {
            Files.createDirectories(appDir);
            Files.copy(file, pending, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(file) != Files.size(pending) || !sha256(file).equals(sha256(pending))) {
                Files.deleteIfExists(pending);
                throw new BusinessException("restore.error.failed");
            }
            Files.writeString(appDir.resolve(PendingRestoreApplier.PENDING_MARKER),
                    file.toAbsolutePath() + System.lineSeparator()
                            + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("restore.error.failed", e.getMessage());
        }
        log.info("Restore scheduled from {}", file);
        return new RestoreApplyResult(true, null);
    }

    /** Latest Flyway version applied to the live database, or null when unknown. */
    public String liveSchemaVersion() {
        try (Connection connection = dataSource.getConnection()) {
            return BackupFileInspector.latestAppliedVersion(connection);
        } catch (SQLException e) {
            return null;
        }
    }

    /** Absolute path of the live SQLite database file. */
    public Path liveDatabasePath() {
        String property = System.getProperty("mebelsex.db.path");
        if (property != null && !property.isBlank()) {
            return Paths.get(property).toAbsolutePath();
        }
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            String path = url.substring("jdbc:sqlite:".length());
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            return Paths.get(path).toAbsolutePath();
        } catch (SQLException e) {
            log.warn("Cannot derive database path", e);
        }
        return Paths.get(System.getProperty("user.home"), com.mebelsex.common.AppInfo.DATA_DIR_NAME,
                PendingRestoreApplier.DB_NAME);
    }

    /** Directory that holds the live DB and the staged restore files. */
    public Path appDataDir() {
        Path parent = liveDatabasePath().getParent();
        if (parent != null) {
            return parent;
        }
        return Paths.get(System.getProperty("user.home"), com.mebelsex.common.AppInfo.DATA_DIR_NAME);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String latestKnownVersion() {
        if (flyway != null) {
            try {
                String best = null;
                for (MigrationInfo info : flyway.info().all()) {
                    if (info.getVersion() != null) {
                        String version = info.getVersion().getVersion();
                        if (best == null || BackupFileInspector.compareVersions(version, best) > 0) {
                            best = version;
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            } catch (RuntimeException e) {
                log.warn("Cannot read Flyway versions", e);
            }
        }
        return "2";
    }

    private static LocalDateTime fileTime(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(),
                    java.time.ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    private long countLive(String table) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
