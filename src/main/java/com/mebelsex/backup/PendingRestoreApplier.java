package com.mebelsex.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Swaps a staged restore file into place on startup, before Spring starts.
 * Pure Java (no Spring) and takes an explicit data directory so tests can use
 * temp folders. Retries the moves for a few seconds to cope with Windows file
 * locks. Always writes a small result marker for the UI to show once.
 */
public final class PendingRestoreApplier {

    public static final String DB_NAME = "mebel.db";
    public static final String PENDING_NAME = "restore_pending.db";
    public static final String PENDING_MARKER = "restore_pending.txt";
    public static final String RESULT_NAME = "restore_result.properties";
    public static final String REPLACED_DIR = "replaced";

    private static final int MAX_ATTEMPTS = 30;
    private static final long RETRY_MILLIS = 500;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private PendingRestoreApplier() {
    }

    /**
     * Applies a pending restore if restore_pending.db exists in dataDir.
     * Returns the result, or null when there was nothing to apply.
     */
    public static RestoreApplyResult apply(Path dataDir) {
        return apply(dataDir, DB_NAME, "mebel_replaced_");
    }

    /** Applies a pending restore for a custom database file name (dev/temp DBs). */
    public static RestoreApplyResult apply(Path dataDir, String dbFileName) {
        return apply(dataDir, dbFileName, "mebel_replaced_");
    }

    /**
     * Applies a pending restore and moves the current database to
     * {@code <dataDir>/replaced/<replacedPrefix><timestamp>.*}. Used by the startup
     * recovery dialog with the {@code mebel_damaged_} prefix.
     */
    public static RestoreApplyResult apply(Path dataDir, String dbFileName, String replacedPrefix) {
        Path pending = dataDir.resolve(PENDING_NAME);
        if (!Files.isRegularFile(pending)) {
            return null;
        }
        Path replacedDir = dataDir.resolve(REPLACED_DIR);
        String stamp = LocalDateTime.now().format(STAMP);

        List<Path[]> moved = new ArrayList<>();
        boolean success = false;
        String failure = null;
        try {
            Files.createDirectories(replacedDir);
            String[] names = { dbFileName, dbFileName + "-wal", dbFileName + "-shm" };
            String[] suffixes = { ".db", "-wal", "-shm" };
            for (int i = 0; i < names.length; i++) {
                Path source = dataDir.resolve(names[i]);
                if (!Files.exists(source)) {
                    continue;
                }
                Path destination = replacedDir.resolve(replacedPrefix + stamp + suffixes[i]);
                moveWithRetry(source, destination);
                moved.add(new Path[] { source, destination });
            }
            moveWithRetry(pending, dataDir.resolve(dbFileName));
            success = true;
        } catch (IOException e) {
            failure = e.getMessage();
            rollback(moved);
        }

        deleteQuietly(dataDir.resolve(PENDING_MARKER));
        RestoreApplyResult result = new RestoreApplyResult(success, success ? null : failure);
        if (success) {
            deleteQuietly(pending);
        }
        writeResult(dataDir, result);
        return result;
    }

    /** Reads and deletes the result marker, returning null when there is none. */
    public static RestoreApplyResult readAndClearResult(Path dataDir) {
        Path result = dataDir.resolve(RESULT_NAME);
        if (!Files.isRegularFile(result)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(result)) {
            properties.load(in);
        } catch (IOException e) {
            deleteQuietly(result);
            return null;
        }
        deleteQuietly(result);
        boolean ok = Boolean.parseBoolean(properties.getProperty("success", "false"));
        return new RestoreApplyResult(ok, properties.getProperty("detail"));
    }

    private static void rollback(List<Path[]> moved) {
        for (Path[] pair : moved) {
            try {
                moveWithRetry(pair[1], pair[0]);
            } catch (IOException ignored) {
                // best effort roll back
            }
        }
    }

    private static void moveWithRetry(Path source, Path destination) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last == null ? new IOException("Cannot move " + source) : last;
    }

    private static void writeResult(Path dataDir, RestoreApplyResult result) {
        Properties properties = new Properties();
        properties.setProperty("success", Boolean.toString(result.success()));
        properties.setProperty("detail", result.detail() == null ? "" : result.detail());
        try (OutputStream out = Files.newOutputStream(dataDir.resolve(RESULT_NAME))) {
            properties.store(out, com.mebelsex.common.AppInfo.PRODUCT_NAME + " restore result");
        } catch (IOException ignored) {
            // nothing more we can do
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
