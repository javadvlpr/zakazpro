package com.mebelsex.ui;

import com.mebelsex.backup.BackupFolderMarker;
import com.mebelsex.backup.PendingRestoreApplier;
import com.mebelsex.backup.RecoveryBackups;
import com.mebelsex.backup.RelaunchHelper;
import com.mebelsex.backup.RestoreApplyResult;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Minimal JavaFX recovery dialog shown by the Launcher when the database is damaged.
 * No Spring, no Flyway and no ThemeManager are available here; it never creates an
 * empty database and never touches the damaged files unless the user restores.
 */
public class RecoveryApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(RecoveryApplication.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int MAX_CANDIDATES = 5;

    @Override
    public void start(Stage stage) {
        ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
        Path dbFile = dbFile();
        Path appDataDir = dbFile == null ? null : dbFile.getParent();

        Label title = new Label(bundle.getString("recovery.title"));
        title.setWrapText(true);
        title.setMinWidth(460);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label message = new Label(bundle.getString("recovery.message"));
        message.setWrapText(true);
        message.setMinWidth(460);

        ListView<RecoveryBackups.Candidate> list = new ListView<>();
        list.setPrefHeight(190);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(RecoveryBackups.Candidate candidate, boolean empty) {
                super.updateItem(candidate, empty);
                setText(empty || candidate == null ? null : formatCandidate(bundle, candidate));
            }
        });

        Label status = new Label();
        status.setWrapText(true);
        status.setMinWidth(460);

        Button restore = new Button(bundle.getString("recovery.restore"));
        restore.setDefaultButton(true);
        Button choose = new Button(bundle.getString("recovery.choose"));
        Button exit = new Button(bundle.getString("recovery.exit"));
        HBox buttons = new HBox(8, restore, choose, exit);

        VBox root = new VBox(12, title, message, list, buttons, status);
        root.setPadding(new Insets(20));

        List<RecoveryBackups.Candidate> candidates = RecoveryBackups.findRecent(searchDirs(appDataDir), MAX_CANDIDATES);
        list.getItems().setAll(candidates);
        if (!candidates.isEmpty()) {
            list.getSelectionModel().select(0);
        } else {
            status.setText(bundle.getString("recovery.no_backups"));
        }

        restore.setOnAction(event -> startRestore(list, appDataDir, dbFile, bundle, status, restore, choose, exit));
        choose.setOnAction(event -> chooseAnother(stage, bundle, list));
        exit.setOnAction(event -> Platform.exit());

        stage.setTitle(bundle.getString("recovery.title"));
        Scene scene = new Scene(root, 540, 470);
        ThemeManager theme = ThemeManager.getInstance();
        if (theme != null) {
            theme.install(scene);
        } else {
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        }
        stage.setScene(scene);
        AppIcons.apply(stage);
        stage.setMinWidth(520);
        stage.setMinHeight(380);
        stage.show();

        snapshotIfDev(root);
    }

    private void startRestore(ListView<RecoveryBackups.Candidate> list, Path appDataDir, Path dbFile,
            ResourceBundle bundle, Label status, Button restore, Button choose, Button exit) {
        RecoveryBackups.Candidate selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status.setText(bundle.getString("recovery.select_first"));
            return;
        }
        restore.setDisable(true);
        choose.setDisable(true);
        exit.setDisable(true);
        status.setText(bundle.getString("recovery.working"));

        Thread worker = new Thread(() -> {
            try {
                RestoreApplyResult result = applyRestore(selected, appDataDir, dbFile);
                if (result == null || !result.success()) {
                    throw new IllegalStateException(result == null ? "apply failed" : result.detail());
                }
                Platform.runLater(() -> {
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle(bundle.getString("recovery.done.title"));
                    done.setHeaderText(null);
                    done.setContentText(bundle.getString("recovery.done.text"));
                    AppIcons.install(done.getDialogPane());
                    done.showAndWait();
                    if (RelaunchHelper.relaunch()) {
                        Platform.exit();
                    } else {
                        status.setText(bundle.getString("recovery.relaunch_failed"));
                        restore.setDisable(false);
                        choose.setDisable(false);
                        exit.setDisable(false);
                    }
                });
            } catch (Exception e) {
                log.error("Recovery restore failed", e);
                Platform.runLater(() -> {
                    status.setText(bundle.getString("recovery.error"));
                    restore.setDisable(false);
                    choose.setDisable(false);
                    exit.setDisable(false);
                });
            }
        }, "recovery-restore");
        worker.setDaemon(true);
        worker.start();
    }

    /** Copies the chosen backup to restore_pending.db and applies it immediately. */
    private RestoreApplyResult applyRestore(RecoveryBackups.Candidate candidate, Path appDataDir, Path dbFile)
            throws Exception {
        if (appDataDir == null || dbFile == null) {
            throw new IllegalStateException("no database path");
        }
        Files.createDirectories(appDataDir);
        Path pending = appDataDir.resolve(PendingRestoreApplier.PENDING_NAME);
        Files.copy(candidate.file(), pending, StandardCopyOption.REPLACE_EXISTING);
        return PendingRestoreApplier.apply(appDataDir, dbFile.getFileName().toString(), "mebel_damaged_");
    }

    private void chooseAnother(Stage stage, ResourceBundle bundle, ListView<RecoveryBackups.Candidate> list) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(bundle.getString("recovery.choose.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(bundle.getString("recovery.filter"), "*.db"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        List<RecoveryBackups.Candidate> found = RecoveryBackups.findRecent(List.of(chosen.toPath().getParent()), 50);
        RecoveryBackups.Candidate match = found.stream()
                .filter(c -> c.file().equals(chosen.toPath().toAbsolutePath().normalize()))
                .findFirst()
                .orElse(null);
        if (match == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(bundle.getString("recovery.title"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("recovery.invalid"));
            AppIcons.install(alert.getDialogPane());
            alert.showAndWait();
            return;
        }
        list.getItems().setAll(match);
        list.getSelectionModel().select(0);
    }

    private static String formatCandidate(ResourceBundle bundle, RecoveryBackups.Candidate candidate) {
        return DATE_TIME.format(candidate.time()) + "   " + formatSize(candidate.size()) + System.lineSeparator()
                + bundle.getString("recovery.customers") + ": " + candidate.customers()
                + "   " + bundle.getString("recovery.orders") + ": " + candidate.orders()
                + "   " + bundle.getString("recovery.payments") + ": " + candidate.payments();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static List<Path> searchDirs(Path appDataDir) {
        String override = System.getenv("MEBELSEX_BACKUP_SEARCH_DIRS");
        if (override != null && !override.isBlank()) {
            List<Path> dirs = new ArrayList<>();
            for (String part : override.split(",")) {
                if (!part.isBlank()) {
                    dirs.add(Paths.get(part.trim()));
                }
            }
            return dirs;
        }
        List<Path> dirs = new ArrayList<>();
        Path recorded = BackupFolderMarker.read(appDataDir);
        if (recorded != null) {
            dirs.add(recorded);
        }
        dirs.add(BackupFolderMarker.defaultFolder());
        return dirs;
    }

    private static Path dbFile() {
        String property = System.getProperty("mebelsex.db.path");
        return property == null || property.isBlank() ? null : Paths.get(property).toAbsolutePath();
    }

    /** Dev-only: snapshot the dialog after layout, then exit (active only with MEBELSEX_SNAPSHOT_DIR). */
    private void snapshotIfDev(VBox root) {
        String dir = System.getenv("MEBELSEX_SNAPSHOT_DIR");
        if (dir == null || dir.isBlank()) {
            return;
        }
        File outDir = new File(dir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        Platform.runLater(() -> {
            try {
                javafx.scene.image.WritableImage image = root.snapshot(new javafx.scene.SnapshotParameters(), null);
                int w = (int) image.getWidth();
                int h = (int) image.getHeight();
                int[] buffer = new int[w * h];
                image.getPixelReader().getPixels(0, 0, w, h,
                        javafx.scene.image.WritablePixelFormat.getIntArgbInstance(), buffer, 0, w);
                BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, w, h, buffer, 0, w);
                ImageIO.write(buffered, "png", new File(outDir, "pC-recovery.png"));
                log.info("Saved recovery snapshot");
            } catch (Exception e) {
                log.warn("Cannot save recovery snapshot", e);
            }
            Platform.exit();
        });
    }
}
