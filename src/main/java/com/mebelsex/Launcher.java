package com.mebelsex;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Plain launcher (does NOT extend Application) — entry point for
 * javafx-maven-plugin.
 * Computes the DB path before JavaFX/Spring start, takes the single-instance
 * lock and applies a staged restore before Spring starts.
 */
public class Launcher {

    /** Wait for the old process to release the lock when started by RelaunchHelper. */
    private static final Duration RELAUNCH_WAIT = Duration.ofSeconds(15);

    public static void main(String[] args) {
        if (com.mebelsex.release.SmokeTestGuard.isSmokeTest(args)) {
            runSmokeTest(args);
            return;
        }

        initDbPath();
        initLogDir();

        boolean relaunched = false;
        if (args != null) {
            for (String arg : args) {
                if ("--relaunch".equals(arg)) {
                    relaunched = true;
                    break;
                }
            }
        }

        com.mebelsex.common.SingleInstanceGuard guard = new com.mebelsex.common.SingleInstanceGuard();
        Path lockFile = lockFileFor(System.getProperty("mebelsex.db.path"));
        boolean acquired = lockFile != null
                && guard.tryAcquire(lockFile, relaunched ? RELAUNCH_WAIT : Duration.ZERO);
        if (!acquired) {
            showAlreadyRunningDialog();
            System.exit(0);
            return;
        }

        // Startup DB safety: a damaged database must never be replaced by a new empty one.
        Path dbFile = dbPath() == null ? null : Paths.get(dbPath());
        if (com.mebelsex.backup.StartupDatabaseCheck.check(dbFile)
                == com.mebelsex.backup.StartupDatabaseCheck.Status.DAMAGED) {
            Application.launch(com.mebelsex.ui.RecoveryApplication.class, args);
            return;
        }

        applyPendingRestore();
        Application.launch(MebelSexApplication.class, args);
    }

    /**
     * Headless self check of a packaged installation: no single-instance lock, no JavaFX,
     * no window. Requires MEBELSEX_DB_PATH (see SmokeTestGuard); otherwise nothing is
     * written and the process exits with code 2.
     */
    private static void runSmokeTest(String[] args) {
        com.mebelsex.release.SmokeTestGuard.Validation validation =
                com.mebelsex.release.SmokeTestGuard.validate(args, System.getenv());
        if (!validation.ok()) {
            com.mebelsex.release.ConsoleOutput.error("SMOKE TEST NOT RUN: " + validation.message());
            System.exit(com.mebelsex.release.SmokeTestGuard.EXIT_INVALID_ENVIRONMENT);
            return;
        }
        initDbPath();
        initLogDir();
        Path dbPath = Paths.get(System.getProperty("mebelsex.db.path"));
        Path resultFile = com.mebelsex.release.SmokeTestGuard.resultFileFor(dbPath);
        int exitCode = com.mebelsex.release.SmokeTestRunner.run(dbPath, resultFile);
        com.mebelsex.release.ConsoleOutput.line("SMOKE TEST " + (exitCode == 0 ? "OK" : "FAILED")
                + " - result file: " + resultFile);
        System.exit(exitCode);
    }

    private static String dbPath() {
        return System.getProperty("mebelsex.db.path");
    }

    /**
     * Sets the {@code mebelsex.log.dir} system property read by logback-spring.xml.
     * Dev runs (MEBELSEX_DB_PATH set) log next to the temp DB, so the real APPDATA
     * folder is never touched; tests never reach the Launcher. MEBELSEX_LOG_DIR wins.
     */
    private static void initLogDir() {
        Path dir;
        String explicit = System.getenv("MEBELSEX_LOG_DIR");
        String devDb = System.getenv("MEBELSEX_DB_PATH");
        if (explicit != null && !explicit.isBlank()) {
            dir = Paths.get(explicit).toAbsolutePath();
        } else if (devDb != null && !devDb.isBlank()) {
            Path parent = Paths.get(devDb).toAbsolutePath().getParent();
            dir = (parent == null ? Paths.get("target") : parent).resolve("logs");
        } else {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = System.getProperty("user.home");
            }
            dir = Paths.get(appData, com.mebelsex.common.AppInfo.DATA_DIR_NAME, "logs");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            // logging falls back to the temp dir defined in logback-spring.xml
        }
        System.setProperty("mebelsex.log.dir", dir.toString());
    }

    /** Lock file next to the database: {@code <dbfilename>.lock}. */
    private static Path lockFileFor(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }
        return Paths.get(dbPath + ".lock");
    }

    /**
     * Minimal JavaFX dialog (light theme is fine, no Spring/ThemeManager available yet).
     * Blocks until the user dismisses it, then the caller exits with code 0.
     */
    private static void showAlreadyRunningDialog() {
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        CountDownLatch done = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                try {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(bundle.getString("startup.already_running.title"));
                    alert.setHeaderText(null);
                    alert.setContentText(bundle.getString("startup.already_running.text"));
                    alert.getButtonTypes().setAll(new ButtonType(bundle.getString("ui.ok")));
                    com.mebelsex.ui.AppIcons.install(alert.getDialogPane());
                    alert.showAndWait();
                } finally {
                    done.countDown();
                }
            });
            done.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(Launcher.class)
                    .warn("Cannot show already-running dialog: {}", e.getMessage());
        }
    }

    /**
     * Swaps a staged restore file into place before Spring (and its DataSource)
     * starts, so the live DB is replaced cleanly. Result marker is shown by the
     * UI after the window appears.
     */
    private static void applyPendingRestore() {
        String dbPath = System.getProperty("mebelsex.db.path");
        if (dbPath == null || dbPath.isBlank()) {
            return;
        }
        Path dir = Paths.get(dbPath).toAbsolutePath().getParent();
        if (dir != null) {
            com.mebelsex.backup.PendingRestoreApplier.apply(dir);
        }
    }

    private static void initDbPath() {
        // Dev-only override (snapshots/tests): never touch the real DB when set.
        String devDb = System.getenv("MEBELSEX_DB_PATH");
        if (devDb != null && !devDb.isBlank()) {
            Path devFile = Paths.get(devDb).toAbsolutePath();
            try {
                if (devFile.getParent() != null) {
                    Files.createDirectories(devFile.getParent());
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create database directory: " + devFile.getParent(), e);
            }
            System.setProperty("mebelsex.db.path", devFile.toString());
            return;
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }
        Path dbDir = Paths.get(appData, com.mebelsex.common.AppInfo.DATA_DIR_NAME);
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create database directory: " + dbDir, e);
        }
        Path dbFile = dbDir.resolve("mebel.db");
        System.setProperty("mebelsex.db.path", dbFile.toAbsolutePath().toString());
    }
}
