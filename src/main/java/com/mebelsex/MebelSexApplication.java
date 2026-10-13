package com.mebelsex;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.mebelsex.backup.BackupPolicy;
import com.mebelsex.backup.BackupReason;
import com.mebelsex.backup.BackupScheduler;
import com.mebelsex.backup.BackupService;
import com.mebelsex.backup.PendingRestoreApplier;
import com.mebelsex.backup.RestoreApplyResult;
import com.mebelsex.settings.AppSettings;
import com.mebelsex.settings.SettingsService;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaFX Application that bootstraps the Spring context.
 * Splash is shown in start(); Spring boots in a background Task; then main
 * window replaces splash.
 */
@SpringBootApplication
public class MebelSexApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(MebelSexApplication.class);

    /** Guarantees at most one global error dialog is on screen at a time (also blocks recursion). */
    private static final AtomicBoolean errorDialogShowing = new AtomicBoolean(false);

    private ConfigurableApplicationContext springContext;
    private Stage mainStage;
    private long startedAtMillis;

    /**
     * Last-resort handler for exceptions that escape the FX thread (and every other thread).
     * Logs with SLF4J and shows the generic Uzbek error dialog exactly once.
     */
    public static void installGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception on thread {}", thread.getName(), throwable);
            Runnable showDialog = () -> {
                if (!errorDialogShowing.compareAndSet(false, true)) {
                    return;
                }
                try {
                    ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
                    com.mebelsex.ui.AlertHelper.error(
                            bundle.getString("ui.error"), bundle.getString("ui.unexpected_error"));
                } finally {
                    errorDialogShowing.set(false);
                }
            };
            if (Platform.isFxApplicationThread()) {
                showDialog.run();
            } else {
                Platform.runLater(showDialog);
            }
        });
    }

    @Override
    public void init() {
        // intentionally empty — Spring is started in a background Task from start()
    }

    @Override
    public void start(Stage primaryStage) {
        startedAtMillis = System.currentTimeMillis();
        installGlobalExceptionHandler();

        // --- splash ---
        Stage splash = new Stage(StageStyle.UNDECORATED);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(60, 60);
        Label productLabel = new Label(com.mebelsex.common.AppInfo.PRODUCT_NAME);
        productLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label label = new Label("Yuklanmoqda...");
        label.setStyle("-fx-font-size: 16px;");
        VBox splashRoot = new VBox(15, productLabel, progress, label);
        splashRoot.setAlignment(Pos.CENTER);
        splashRoot.setStyle("-fx-padding: 40; -fx-background-color: #F3F6FB;");
        splash.setScene(new Scene(splashRoot, 300, 200));
        com.mebelsex.ui.AppIcons.apply(splash);
        splash.show();

        // Dev-only: force the startup-failure dialog so its layout can be snapshotted.
        if (System.getenv("MEBELSEX_FORCE_STARTUP_FAILURE") != null) {
            showStartupFailure(new RuntimeException(
                    "Schema `main` has a version (3) that is newer than the latest available migration (2)."));
            return;
        }

        // --- boot Spring in background ---
        Task<ConfigurableApplicationContext> bootTask = new Task<>() {
            @Override
            protected ConfigurableApplicationContext call() {
                return new SpringApplicationBuilder(MebelSexApplication.class)
                        .headless(false)
                        .run();
            }
        };

        bootTask.setOnSucceeded(event -> {
            springContext = bootTask.getValue();
            try {
                // Apply the stored theme before any window is built (no flash).
                com.mebelsex.ui.ThemeManager themeManager =
                        springContext.getBean(com.mebelsex.ui.ThemeManager.class);
                themeManager.loadFromSettings();

                com.mebelsex.license.LicenseService licenseService =
                        springContext.getBean(com.mebelsex.license.LicenseService.class);
                com.mebelsex.license.LicenseStatus licenseStatus = licenseService.evaluate();
                if (licenseStatus.state() == com.mebelsex.license.LicenseStatus.State.BLOCKED) {
                    showActivation(primaryStage, splash, themeManager, licenseService);
                } else {
                    showMainWindow(primaryStage, splash, themeManager);
                }
                // Only after a usable window exists (never while BLOCKED).
                licenseService.startPeriodicChecks();
            } catch (Exception e) {
                splash.close();
                showStartupFailure(e);
            }
        });

        bootTask.setOnFailed(event -> {
            splash.close();
            showStartupFailure(bootTask.getException());
        });

        Thread bootThread = new Thread(bootTask, "spring-boot-init");
        bootThread.setDaemon(true);
        bootThread.start();
    }

    /** Builds and shows the normal main window (VALID or READ_ONLY). */
    private void showMainWindow(Stage primaryStage, Stage splash, com.mebelsex.ui.ThemeManager themeManager) {
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main.fxml"), bundle);
        loader.setControllerFactory(springContext::getBean);
        Parent root;
        try {
            root = loader.load();
        } catch (Exception e) {
            splash.close();
            showStartupFailure(e);
            return;
        }

        String winSize = System.getenv("MEBELSEX_WINDOW_SIZE");
        int initW = 1280;
        int initH = 800;
        if (winSize != null && winSize.contains("x")) {
            String[] parts = winSize.split("x");
            initW = Integer.parseInt(parts[0]);
            initH = Integer.parseInt(parts[1]);
        }
        Scene scene = new Scene(root, initW, initH);
        themeManager.install(scene);

        primaryStage.setTitle(com.mebelsex.common.AppInfo.PRODUCT_NAME);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(680);
        boolean devSize = winSize != null && winSize.contains("x");
        if (devSize) {
            primaryStage.centerOnScreen();
        } else {
            applySavedGeometry(primaryStage);
        }
        mainStage = primaryStage;
        splash.close();
        primaryStage.show();

        showRestoreResultIfAny();
        setupCloseHook(primaryStage);
        logStartupSummary();

        String snapDir = System.getenv("MEBELSEX_SNAPSHOT_DIR");
        if (snapDir != null) {
            com.mebelsex.ui.SnapshotConfigurator config = new com.mebelsex.ui.SnapshotConfigurator();
            config.runSnapshots(primaryStage, springContext, snapDir);
        }
    }

    /**
     * BLOCKED: only the activation window is shown; the main window is not built and
     * no backup runs. A successful import continues to the main window in this process.
     */
    private void showActivation(Stage primaryStage, Stage splash, com.mebelsex.ui.ThemeManager themeManager,
            com.mebelsex.license.LicenseService licenseService) {
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/activation.fxml"), bundle);
        loader.setControllerFactory(springContext::getBean);
        Parent root;
        try {
            root = loader.load();
        } catch (Exception e) {
            splash.close();
            showStartupFailure(e);
            return;
        }
        com.mebelsex.ui.ActivationController controller = loader.getController();
        Scene scene = new Scene(root);
        themeManager.install(scene);

        controller.setOnImported(status -> showMainWindow(primaryStage, splash, themeManager));

        primaryStage.setTitle(bundle.getString("license.activation.title"));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(560);
        primaryStage.setMinHeight(360);
        primaryStage.setOnCloseRequest(event -> Platform.exit());
        root.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                Platform.exit();
            }
        });
        stageSizeForActivation(primaryStage);
        splash.close();
        primaryStage.show();
        root.requestFocus();
        snapshotActivationIfDev(primaryStage);
    }

    /** Activation window is centred at a comfortable size (never the saved main-window size). */
    private void stageSizeForActivation(Stage stage) {
        stage.setWidth(620);
        stage.setHeight(430);
        stage.centerOnScreen();
    }

    /** Dev-only: snapshot the activation window and exit when a snapshot dir is set. */
    private void snapshotActivationIfDev(Stage stage) {
        String snapDir = System.getenv("MEBELSEX_SNAPSHOT_DIR");
        if (snapDir == null || snapDir.isBlank()) {
            return;
        }
        File outDir = new File(snapDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
        wait.setOnFinished(e -> {
            savePng((Parent) stage.getScene().getRoot(), new File(outDir, "lic-activation.png"));
            Platform.exit();
            System.exit(0);
        });
        wait.play();
    }

    /** Restores the saved geometry only when it still intersects a connected screen. */
    private void applySavedGeometry(Stage stage) {
        try {
            SettingsService settings = springContext.getBean(SettingsService.class);
            com.mebelsex.settings.WindowState state = settings.loadWindow();
            java.util.List<com.mebelsex.ui.WindowGeometry.Rect> screens = javafx.stage.Screen.getScreens().stream()
                    .map(screen -> {
                        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
                        return new com.mebelsex.ui.WindowGeometry.Rect(bounds.getMinX(), bounds.getMinY(),
                                bounds.getWidth(), bounds.getHeight());
                    })
                    .collect(java.util.stream.Collectors.toList());
            if (state.hasBounds()) {
                com.mebelsex.ui.WindowGeometry.Rect resolved = com.mebelsex.ui.WindowGeometry.resolve(
                        new com.mebelsex.ui.WindowGeometry.Rect(state.x(), state.y(), state.width(), state.height()),
                        screens, 1024, 680);
                stage.setX(resolved.x());
                stage.setY(resolved.y());
                stage.setWidth(resolved.width());
                stage.setHeight(resolved.height());
            } else {
                stage.centerOnScreen();
            }
            if (state.maximized()) {
                stage.setMaximized(true);
            }
        } catch (RuntimeException e) {
            log.warn("Cannot restore window geometry", e);
            stage.centerOnScreen();
        }
    }

    private void saveWindowGeometry(Stage stage) {
        try {
            SettingsService settings = springContext.getBean(SettingsService.class);
            settings.saveWindow(new com.mebelsex.settings.WindowState(
                    (int) Math.round(stage.getX()), (int) Math.round(stage.getY()),
                    (int) Math.round(stage.getWidth()), (int) Math.round(stage.getHeight()),
                    stage.isMaximized()));
        } catch (RuntimeException e) {
            log.warn("Cannot save window geometry", e);
        }
    }

    /**
     * Shows the Uzbek "Dastur ishga tushmadi" dialog with a short reason (no stack
     * trace to the user), logs the full stack trace and exits with a non-zero code.
     * Minimal path: ThemeManager/Spring are not available on this path (light theme).
     */
    private void showStartupFailure(Throwable failure) {
        log.error("Application startup failed", failure);
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        String reasonKey = com.mebelsex.common.StartupFailureMapper.messageKey(failure);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("startup.failed.title"));
        alert.setHeaderText(null);
        alert.setContentText(bundle.getString(reasonKey));
        ButtonType logsButton = new ButtonType(bundle.getString("startup.failed.open_logs"),
                ButtonBar.ButtonData.LEFT);
        ButtonType exitButton = new ButtonType(bundle.getString("startup.failed.exit"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(logsButton, exitButton);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        com.mebelsex.ui.AppIcons.install(alert.getDialogPane());
        try {
            if (alert.getDialogPane().getScene().getStylesheets().isEmpty()) {
                alert.getDialogPane().getScene().getStylesheets()
                        .add(getClass().getResource("/css/application.css").toExternalForm());
            }
        } catch (Exception ignored) {
            // styling is best effort on the failure path
        }
        String snapDir = System.getenv("MEBELSEX_SNAPSHOT_DIR");
        if (snapDir != null && !snapDir.isBlank()) {
            File outDir = new File(snapDir);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            alert.show();
            Platform.runLater(() -> {
                savePng(alert.getDialogPane(), new File(outDir, "pC-startup-failure.png"));
                Platform.exit();
                System.exit(0);
            });
            return;
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == logsButton) {
            com.mebelsex.common.FileExplorer.openFolder(logDir());
        }
        Platform.exit();
        System.exit(1);
    }

    /** Dev-only PNG writer used by the snapshot hooks. */
    private static void savePng(Parent root, File dest) {
        try {
            javafx.scene.image.WritableImage image = root.snapshot(new javafx.scene.SnapshotParameters(), null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            int[] buffer = new int[w * h];
            image.getPixelReader().getPixels(0, 0, w, h,
                    javafx.scene.image.WritablePixelFormat.getIntArgbInstance(), buffer, 0, w);
            java.awt.image.BufferedImage buffered =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            buffered.setRGB(0, 0, w, h, buffer, 0, w);
            javax.imageio.ImageIO.write(buffered, "png", dest);
            log.info("Saved snapshot {}", dest.getName());
        } catch (Exception e) {
            log.warn("Cannot save snapshot", e);
        }
    }

    /** Log directory (system property set by the Launcher; APPDATA fallback). */
    private static Path logDir() {
        String property = System.getProperty("mebelsex.log.dir");
        if (property != null && !property.isBlank()) {
            return Paths.get(property);
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }
        return Paths.get(appData, com.mebelsex.common.AppInfo.DATA_DIR_NAME, "logs");
    }

    /** Shows (once) the outcome of a restore that was applied before startup. */
    private void showRestoreResultIfAny() {
        Path dir = appDataDir();
        if (dir == null) {
            return;
        }
        RestoreApplyResult result = PendingRestoreApplier.readAndClearResult(dir);
        if (result == null) {
            return;
        }
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        String title = bundle.getString("restore.result.title");
        if (result.success()) {
            com.mebelsex.ui.AlertHelper.info(title, bundle.getString("restore.result.success"));
        } else {
            String detail = result.detail() == null || result.detail().isBlank() ? "" : System.lineSeparator() + result.detail();
            com.mebelsex.ui.AlertHelper.error(title, bundle.getString("restore.result.failed") + detail);
        }
    }

    private Path appDataDir() {
        String dbPath = System.getProperty("mebelsex.db.path");
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }
        return Paths.get(dbPath).toAbsolutePath().getParent();
    }

    /**
     * On close, if the close backup is enabled and data changed since the last
     * backup, run one in the background and then exit. Failures never block the
     * exit (the backup runs after the window is already closing).
     */
    private void setupCloseHook(Stage primaryStage) {
        BackupScheduler scheduler = springContext.getBean(BackupScheduler.class);
        if (!scheduler.isEnabled()) {
            return;
        }
        SettingsService settingsService = springContext.getBean(SettingsService.class);
        BackupService backupService = springContext.getBean(BackupService.class);
        com.mebelsex.ui.TaskRunner taskRunner = springContext.getBean(com.mebelsex.ui.TaskRunner.class);
        AtomicBoolean closing = new AtomicBoolean(false);
        primaryStage.setOnCloseRequest(event -> {
            if (closing.get()) {
                return;
            }
            AppSettings settings = settingsService.load();
            if (!settings.backupOnClose()) {
                return;
            }
            event.consume();
            closing.set(true);
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    LocalDateTime last = settings.backupLastSuccess();
                    if (BackupPolicy.shouldRunOnClose(true, backupService.dataChangedSince(last))) {
                        backupService.createBackup(BackupReason.ON_CLOSE);
                    }
                    return null;
                }
            };
            taskRunner.run(task, value -> Platform.exit(), error -> Platform.exit());
        });
    }

    /** One startup line with the environment (ids/paths only, never business data). */
    private void logStartupSummary() {
        String flyway = "?";
        String backup = "?";
        String theme = "?";
        try {
            flyway = springContext.getBean(com.mebelsex.backup.RestoreService.class).liveSchemaVersion();
            backup = springContext.getBean(BackupScheduler.class).isEnabled() ? "on" : "off";
            theme = springContext.getBean(com.mebelsex.ui.ThemeManager.class).current().name();
        } catch (RuntimeException ignored) {
            // summary is best effort
        }
        log.info("Started {} {} | Java {} | OS {} {} | DB {} | flyway {} | backup scheduler {} | theme {}",
                com.mebelsex.common.AppInfo.PRODUCT_NAME, com.mebelsex.common.AppInfo.version(),
                System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("mebelsex.db.path"), flyway, backup, theme);
    }

    @Override
    public void stop() {
        long uptimeSeconds = startedAtMillis == 0 ? 0 : (System.currentTimeMillis() - startedAtMillis) / 1000;
        log.info("Shutting down {} after {} s", com.mebelsex.common.AppInfo.PRODUCT_NAME, uptimeSeconds);
        if (mainStage != null && springContext != null) {
            saveWindowGeometry(mainStage);
        }
        if (springContext != null) {
            springContext.close();
        }
        String devDb = System.getenv("MEBELSEX_DB_PATH");
        if (devDb != null && !devDb.isBlank()) {
            java.io.File dbFile = new java.io.File(devDb);
            String path = dbFile.getAbsolutePath();
            try {
                if (dbFile.exists())
                    dbFile.delete();
                java.io.File wal = new java.io.File(path + "-wal");
                if (wal.exists())
                    wal.delete();
                java.io.File shm = new java.io.File(path + "-shm");
                if (shm.exists())
                    shm.delete();
                log.info("Deleted temporary snapshot DB files");
            } catch (Exception e) {
                log.warn("Cannot delete temporary snapshot DB files", e);
            }
        }
    }
}
