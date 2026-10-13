package com.mebelsex.ui.settings;

import com.mebelsex.backup.BackupFileRow;
import com.mebelsex.backup.BackupReason;
import com.mebelsex.backup.BackupResult;
import com.mebelsex.backup.BackupService;
import com.mebelsex.backup.BackupStatus;
import com.mebelsex.backup.RelaunchHelper;
import com.mebelsex.backup.RestorePreview;
import com.mebelsex.backup.RestoreService;
import com.mebelsex.common.BusinessException;
import com.mebelsex.common.DiagnosticsBuilder;
import com.mebelsex.common.FileExplorer;
import com.mebelsex.license.LicenseService;
import com.mebelsex.license.LicenseStatus;
import com.mebelsex.settings.AppSettings;
import com.mebelsex.settings.SettingsService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/** Sozlamalar screen: company, backup (auto/manual/restore) and about. */
@Controller
@Scope("prototype")
public class SettingsController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int RECENT_LIMIT = 15;

    private final SettingsService settingsService;
    private final BackupService backupService;
    private final RestoreService restoreService;
    private final TaskRunner taskRunner;
    private final com.mebelsex.ui.ThemeManager themeManager;
    private final LicenseService licenseService;
    private final ResourceBundle bundle;

    @FXML
    private Button saveButton;
    @FXML
    private TextField companyNameField;
    @FXML
    private TextField companyPhoneField;
    @FXML
    private TextField companyAddressField;
    @FXML
    private TextField backupFolderField;
    @FXML
    private Button chooseFolderButton;
    @FXML
    private Button openFolderButton;
    @FXML
    private CheckBox backupAutoCheck;
    @FXML
    private ComboBox<String> backupHourCombo;
    @FXML
    private ComboBox<String> backupMinuteCombo;
    @FXML
    private CheckBox backupOnCloseCheck;
    @FXML
    private TextField keepDaysField;
    @FXML
    private Button backupNowButton;
    @FXML
    private Label backupStatusLabel;
    @FXML
    private TableView<BackupFileRow> recentTable;
    @FXML
    private Button restoreButton;
    @FXML
    private Label versionLabel;
    @FXML
    private TextField dbPathField;
    @FXML
    private Button openLogsButton;
    @FXML
    private Button createDiagnosticsButton;
    @FXML
    private javafx.scene.control.ToggleButton lightThemeToggle;
    @FXML
    private javafx.scene.control.ToggleButton darkThemeToggle;
    @FXML
    private Label licenseStateLabel;
    @FXML
    private Label licenseDevLabel;
    @FXML
    private Label licenseCustomerLabel;
    @FXML
    private Label licenseValidUntilLabel;
    @FXML
    private TextField licenseMachineField;
    @FXML
    private Button licenseCopyButton;
    @FXML
    private Label licenseIdLabel;
    @FXML
    private Button licenseChooseButton;
    @FXML
    private Label licenseRenewHint;

    public SettingsController(SettingsService settingsService, BackupService backupService,
            RestoreService restoreService, TaskRunner taskRunner, com.mebelsex.ui.ThemeManager themeManager,
            LicenseService licenseService) {
        this.settingsService = settingsService;
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.taskRunner = taskRunner;
        this.themeManager = themeManager;
        this.licenseService = licenseService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        setupTimeCombos();
        setupRecentTable();
        setupThemeToggles();

        AppSettings settings = settingsService.load();
        companyNameField.setText(settings.companyName());
        companyPhoneField.setText(settings.companyPhone());
        companyAddressField.setText(settings.companyAddress());
        backupFolderField.setText(settings.backupFolder().isBlank()
                ? settingsService.defaultBackupFolder().toString()
                : settings.backupFolder());
        backupAutoCheck.setSelected(settings.backupAutoEnabled());
        backupOnCloseCheck.setSelected(settings.backupOnClose());
        keepDaysField.setText(Integer.toString(settings.backupKeepDays()));
        applyTime(settings.backupTime());

        versionLabel.setText(com.mebelsex.common.AppInfo.PRODUCT_NAME + " " + version());
        dbPathField.setText(restoreService.liveDatabasePath().toString());

        refreshLicenseCard();
        refreshBackupStatus();
        loadRecentBackups();
        updateWindowTitle(settings.companyName());
    }

    // ------------------------------------------------------------------
    // license
    // ------------------------------------------------------------------

    /** Fills the Litsenziya card from the current license status. */
    private void refreshLicenseCard() {
        LicenseStatus status = licenseService.current();
        licenseStateLabel.setText(bundle.getString(stateKey(status.state())));
        licenseStateLabel.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        licenseStateLabel.getStyleClass()
                .add(status.state() == LicenseStatus.State.VALID ? "stat-card-profit" : "stat-card-debt");

        licenseCustomerLabel.setText(status.customer() == null
                ? bundle.getString("settings.license.none") : status.customer());
        licenseValidUntilLabel.setText(validUntilText(status));
        licenseMachineField.setText(licenseService.machineId().orElse(""));
        String id = status.licenseId();
        licenseIdLabel.setText(id == null ? bundle.getString("settings.license.none") : shortId(id));

        boolean dev = licenseService.devMode();
        licenseDevLabel.setManaged(dev);
        licenseDevLabel.setVisible(dev);
        boolean readOnly = status.state() == LicenseStatus.State.READ_ONLY;
        licenseRenewHint.setManaged(readOnly);
        licenseRenewHint.setVisible(readOnly);
    }

    private String validUntilText(LicenseStatus status) {
        if (status.expiresAt() == null) {
            return bundle.getString("settings.license.no_expiry");
        }
        String text = com.mebelsex.common.Formatters.formatDate(status.expiresAt());
        if (status.daysLeft() != null && status.daysLeft() >= 0) {
            text = text + " (" + com.mebelsex.common.Messages.format(
                    "settings.license.days_left", status.daysLeft()) + ")";
        }
        return text;
    }

    private String stateKey(LicenseStatus.State state) {
        return switch (state) {
            case VALID -> "settings.license.state.valid";
            case READ_ONLY -> "settings.license.state.readonly";
            case BLOCKED -> "settings.license.state.blocked";
        };
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    @FXML
    public void handleCopyMachineId() {
        String machine = licenseMachineField.getText();
        if (machine == null || machine.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(machine);
        Clipboard.getSystemClipboard().setContent(content);
        AlertHelper.info(bundle.getString("settings.license.title"), bundle.getString("license.activation.copied"));
    }

    @FXML
    public void handleImportLicense() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(bundle.getString("license.activation.choose.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                bundle.getString("license.activation.filter"), "*.lic", "*.LIC"));
        File chosen = chooser.showOpenDialog(AlertHelper.getActiveWindow(Window.getWindows()));
        if (chosen == null) {
            return;
        }
        licenseChooseButton.setDisable(true);
        taskRunner.run(() -> licenseService.importLicense(chosen.toPath()), status -> {
            licenseChooseButton.setDisable(false);
            refreshLicenseCard();
            AlertHelper.info(bundle.getString("settings.license.title"),
                    bundle.getString("license.activation.imported"));
        }, error -> licenseChooseButton.setDisable(false));
    }

    /** "Ko'rinish" card: two toggles bound to ThemeManager; switching applies immediately. */
    private void setupThemeToggles() {
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        lightThemeToggle.setToggleGroup(group);
        darkThemeToggle.setToggleGroup(group);
        if (themeManager.current() == com.mebelsex.ui.Theme.DARK) {
            darkThemeToggle.setSelected(true);
        } else {
            lightThemeToggle.setSelected(true);
        }
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            themeManager.setTheme(now == darkThemeToggle ? com.mebelsex.ui.Theme.DARK : com.mebelsex.ui.Theme.LIGHT);
        });
        themeManager.themeProperty().addListener((obs, old, now) -> {
            javafx.scene.control.ToggleButton target = now == com.mebelsex.ui.Theme.DARK ? darkThemeToggle
                    : lightThemeToggle;
            if (!target.isSelected()) {
                target.setSelected(true);
            }
        });
    }

    // ------------------------------------------------------------------
    // save
    // ------------------------------------------------------------------

    @FXML
    public void handleSave() {
        try {
            int keepDays = parseKeepDays();
            String time = selected(backupHourCombo) + ":" + selected(backupMinuteCombo);
            String name = companyNameField.getText() == null ? "" : companyNameField.getText().trim();
            settingsService.saveCompany(name,
                    safe(companyPhoneField.getText()), safe(companyAddressField.getText()));
            settingsService.saveBackup(safe(backupFolderField.getText()), backupAutoCheck.isSelected(), time,
                    backupOnCloseCheck.isSelected(), keepDays);
            backupService.rememberBackupFolder();
            updateWindowTitle(name);
            refreshBackupStatus();
            AlertHelper.info(bundle.getString("menu.settings"), bundle.getString("settings.save.success"));
        } catch (BusinessException e) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString(e.getMessageKey()));
        }
    }

    private int parseKeepDays() {
        String raw = keepDaysField.getText() == null ? "" : keepDaysField.getText().trim();
        if (raw.isEmpty()) {
            return SettingsService.DEFAULT_KEEP_DAYS;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException("settings.error.keep_days");
        }
    }

    // ------------------------------------------------------------------
    // backup
    // ------------------------------------------------------------------

    @FXML
    public void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(bundle.getString("settings.backup.choose.title"));
        File current = new File(safe(backupFolderField.getText()));
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File chosen = chooser.showDialog(AlertHelper.getActiveWindow(Window.getWindows()));
        if (chosen != null) {
            backupFolderField.setText(chosen.getAbsolutePath());
        }
    }

    @FXML
    public void handleOpenFolder() {
        openInExplorer(Path.of(safe(backupFolderField.getText())), false);
    }

    @FXML
    public void handleBackupNow() {
        backupNowButton.setDisable(true);
        taskRunner.run(() -> backupService.createBackup(BackupReason.MANUAL), result -> {
            backupNowButton.setDisable(false);
            refreshBackupStatus();
            loadRecentBackups();
            showBackupDone(result);
        }, error -> backupNowButton.setDisable(false));
    }

    private void showBackupDone(BackupResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(bundle.getString("settings.backup.done.title"));
        alert.setHeaderText(null);
        alert.setContentText(new MessageFormat(bundle.getString("settings.backup.done.text"))
                .format(new Object[] { result.path().toString() }));
        ButtonType show = new ButtonType(bundle.getString("settings.backup.show"));
        alert.getButtonTypes().setAll(show, ButtonType.OK);
        prepare(alert);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == show) {
            openInExplorer(result.path(), true);
        }
    }

    // ------------------------------------------------------------------
    // diagnostics (Dastur haqida)
    // ------------------------------------------------------------------

    @FXML
    public void handleOpenLogs() {
        FileExplorer.openFolder(logDir());
    }

    @FXML
    public void handleCreateDiagnostics() {
        createDiagnosticsButton.setDisable(true);
        taskRunner.run(() -> DiagnosticsBuilder.create(diagnosticsDir(), logDir(), diagnosticsInfo()),
                zip -> {
                    createDiagnosticsButton.setDisable(false);
                    showDiagnosticsDone(zip);
                },
                error -> createDiagnosticsButton.setDisable(false));
    }

    private void showDiagnosticsDone(Path zip) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(bundle.getString("settings.about.diagnostics.done.title"));
        alert.setHeaderText(null);
        alert.setContentText(new MessageFormat(bundle.getString("settings.about.diagnostics.done.text"))
                .format(new Object[] { zip.toString() }));
        ButtonType show = new ButtonType(bundle.getString("settings.about.diagnostics.show"));
        alert.getButtonTypes().setAll(show, ButtonType.OK);
        prepare(alert);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == show) {
            FileExplorer.selectFile(zip);
        }
    }

    /** Ordered info.txt lines (version, Java, OS, DB, Flyway, backup, theme, date). */
    private List<String> diagnosticsInfo() {
        Path db = restoreService.liveDatabasePath();
        long dbSize = 0;
        try {
            if (Files.exists(db)) {
                dbSize = Files.size(db);
            }
        } catch (IOException ignored) {
            // size stays 0
        }
        String flyway = restoreService.liveSchemaVersion();
        BackupStatus status = backupService.status();
        String lastBackup = status.lastSuccess() == null
                ? bundle.getString("diagnostics.backup.never")
                : DATE_TIME.format(status.lastSuccess());
        String theme = themeManager.current() == com.mebelsex.ui.Theme.DARK
                ? bundle.getString("settings.appearance.dark")
                : bundle.getString("settings.appearance.light");

        List<String> lines = new ArrayList<>();
        lines.add(bundle.getString("diagnostics.info.product") + ": " + com.mebelsex.common.AppInfo.PRODUCT_NAME);
        lines.add(bundle.getString("diagnostics.info.version") + ": " + version());
        lines.add(bundle.getString("diagnostics.info.java") + ": " + System.getProperty("java.version"));
        lines.add(bundle.getString("diagnostics.info.os") + ": "
                + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        lines.add(bundle.getString("diagnostics.info.db_path") + ": " + db);
        lines.add(bundle.getString("diagnostics.info.db_size") + ": " + formatSize(dbSize));
        lines.add(bundle.getString("diagnostics.info.flyway") + ": " + (flyway == null ? "-" : flyway));
        lines.add(bundle.getString("diagnostics.info.backup") + ": " + lastBackup
                + " (" + status.folder() + ")");
        lines.add(bundle.getString("diagnostics.info.theme") + ": " + theme);
        lines.add(bundle.getString("diagnostics.info.date") + ": " + DATE_TIME.format(LocalDateTime.now()));
        return lines;
    }

    /** Log directory (system property set by the Launcher; APPDATA fallback). */
    static Path logDir() {
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

    /** Diagnostics output folder; dev/test override keeps the real Documents folder untouched. */
    static Path diagnosticsDir() {
        String override = System.getenv("MEBELSEX_DIAGNOSTICS_DIR");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home"), "Documents",
                com.mebelsex.common.AppInfo.DOCUMENTS_DIR_NAME, "Diagnostika");
    }

    // ------------------------------------------------------------------
    // restore
    // ------------------------------------------------------------------

    @FXML
    public void handleRestoreFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(bundle.getString("settings.restore.choose.title"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(bundle.getString("settings.restore.filter"), "*.db"));
        File chosen = chooser.showOpenDialog(AlertHelper.getActiveWindow(Window.getWindows()));
        if (chosen != null) {
            restoreFrom(chosen.toPath());
        }
    }

    /** Inspects the file and, when valid, asks before staging a restore. */
    public void restoreFrom(Path file) {
        taskRunner.run(() -> restoreService.inspect(file), preview -> {
            if (!preview.valid()) {
                AlertHelper.error(bundle.getString("settings.restore.title"), bundle.getString(preview.reasonKey()));
                return;
            }
            if (confirmRestore(preview)) {
                scheduleRestore(file);
            }
        });
    }

    private boolean confirmRestore(RestorePreview preview) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("settings.restore.preview.title"));
        alert.setHeaderText(null);
        alert.setContentText(bundle.getString("settings.restore.warning"));

        HBox fileRow = new HBox(8,
                new Label(bundle.getString("settings.restore.preview.file_time")),
                new Label(preview.fileTime() == null ? "\u2014" : DATE_TIME.format(preview.fileTime())));
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(6);
        grid.add(new Label(""), 0, 0);
        grid.add(new Label(bundle.getString("settings.restore.preview.backup")), 1, 0);
        grid.add(new Label(bundle.getString("settings.restore.preview.current")), 2, 0);
        addRow(grid, 1, bundle.getString("settings.restore.preview.customers"),
                preview.customers(), preview.currentCustomers());
        addRow(grid, 2, bundle.getString("settings.restore.preview.orders"),
                preview.orders(), preview.currentOrders());
        addRow(grid, 3, bundle.getString("settings.restore.preview.payments"),
                preview.payments(), preview.currentPayments());
        alert.getDialogPane().setContent(new VBox(10, fileRow, grid));

        alert.getButtonTypes().setAll(AlertHelper.YES_BUTTON, AlertHelper.NO_BUTTON);
        prepare(alert);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == AlertHelper.YES_BUTTON;
    }

    private void addRow(GridPane grid, int row, String label, long backup, long current) {
        grid.add(new Label(label), 0, row);
        grid.add(new Label(Long.toString(backup)), 1, row);
        grid.add(new Label(Long.toString(current)), 2, row);
    }

    private void scheduleRestore(Path file) {
        taskRunner.run(() -> restoreService.scheduleRestore(file), result -> {
            AlertHelper.info(bundle.getString("settings.restore.title"),
                    bundle.getString("settings.restore.scheduled"));
            if (RelaunchHelper.relaunch()) {
                Platform.exit();
            } else {
                AlertHelper.info(bundle.getString("settings.restore.title"),
                        bundle.getString("settings.restore.relaunch_failed"));
            }
        });
    }

    // ------------------------------------------------------------------
    // recent backups table
    // ------------------------------------------------------------------

    private void setupRecentTable() {
        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BackupFileRow, String> dateCol = new TableColumn<>(bundle.getString("settings.backup.table.date"));
        dateCol.setCellValueFactory(d -> new SimpleObjectProperty<>(DATE.format(d.getValue().dateTime().toLocalDate())));
        dateCol.setPrefWidth(110);

        TableColumn<BackupFileRow, String> timeCol = new TableColumn<>(bundle.getString("settings.backup.table.time"));
        timeCol.setCellValueFactory(d -> new SimpleObjectProperty<>(HH_MM.format(d.getValue().dateTime())));
        timeCol.setPrefWidth(80);

        TableColumn<BackupFileRow, String> sizeCol = new TableColumn<>(bundle.getString("settings.backup.table.size"));
        sizeCol.setCellValueFactory(d -> new SimpleObjectProperty<>(formatSize(d.getValue().sizeBytes())));
        sizeCol.setPrefWidth(100);

        TableColumn<BackupFileRow, BackupFileRow> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(220);
        actionCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        actionCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BackupFileRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                Button show = new Button(bundle.getString("settings.backup.show"));
                show.getStyleClass().add("secondary-button");
                show.setOnAction(e -> openInExplorer(row.path(), true));

                Button restore = new Button(bundle.getString("settings.backup.restore"));
                restore.getStyleClass().add("danger-button");
                restore.setOnAction(e -> restoreFrom(row.path()));

                HBox box = new HBox(8, show, restore);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        recentTable.getColumns().setAll(List.of(dateCol, timeCol, sizeCol, actionCol));
    }

    private void loadRecentBackups() {
        List<BackupFileRow> rows = backupService.listRecent(RECENT_LIMIT);
        recentTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void refreshBackupStatus() {
        BackupStatus status = backupService.status();
        String text;
        String style = null;
        if (status.neverRun()) {
            text = bundle.getString("settings.backup.never");
            style = "stat-card-debt";
        } else if (status.ageDays() != null && status.ageDays() == 0) {
            text = new MessageFormat(bundle.getString("settings.backup.today"))
                    .format(new Object[] { HH_MM.format(status.lastSuccess().toLocalTime()) });
            style = "stat-card-profit";
        } else {
            text = new MessageFormat(bundle.getString("settings.backup.days_ago"))
                    .format(new Object[] { String.valueOf(status.ageDays()) });
            if (status.ageDays() != null && status.ageDays() > 2) {
                style = "stat-card-debt";
            }
        }
        backupStatusLabel.setText(new MessageFormat(bundle.getString("settings.backup.last"))
                .format(new Object[] { text }));
        backupStatusLabel.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        if (style != null) {
            backupStatusLabel.getStyleClass().add(style);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void setupTimeCombos() {
        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(String.format("%02d", h));
        }
        List<String> minutes = new ArrayList<>();
        for (int m = 0; m < 60; m += 5) {
            minutes.add(String.format("%02d", m));
        }
        backupHourCombo.setItems(FXCollections.observableArrayList(hours));
        backupMinuteCombo.setItems(FXCollections.observableArrayList(minutes));
    }

    private void applyTime(String time) {
        String hour = "00";
        String minute = "00";
        if (time != null && time.matches("\\d{2}:\\d{2}")) {
            hour = time.substring(0, 2);
            int m = Integer.parseInt(time.substring(3, 5));
            int rounded = (m / 5) * 5;
            minute = String.format("%02d", rounded);
        }
        backupHourCombo.getSelectionModel().select(hour);
        backupMinuteCombo.getSelectionModel().select(minute);
    }

    private String selected(ComboBox<String> combo) {
        String value = combo.getSelectionModel().getSelectedItem();
        return value == null ? "00" : value;
    }

    private void updateWindowTitle(String companyName) {
        if (saveButton == null || saveButton.getScene() == null) {
            return;
        }
        if (saveButton.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle(com.mebelsex.common.AppInfo.windowTitle(companyName));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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

    private String version() {
        return com.mebelsex.common.AppInfo.version();
    }

    private void openInExplorer(Path path, boolean select) {
        try {
            if (select) {
                new ProcessBuilder("explorer.exe", "/select," + path.toAbsolutePath()).start();
            } else {
                new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
            }
        } catch (Exception e) {
            // Fallback: best effort with the file manager
            try {
                new ProcessBuilder("explorer.exe", path.toAbsolutePath().getParent().toString()).start();
            } catch (Exception ignored) {
                // nothing else to do
            }
        }
    }

    private void prepare(Alert alert) {
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Window owner = AlertHelper.getActiveWindow(Window.getWindows());
        if (owner != null) {
            alert.initOwner(owner);
        }
        if (alert.getDialogPane().getScene().getStylesheets().isEmpty()) {
            alert.getDialogPane().getScene().getStylesheets()
                    .add(getClass().getResource("/css/application.css").toExternalForm());
        }
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        themeManager.install(alert.getDialogPane());
    }
}
