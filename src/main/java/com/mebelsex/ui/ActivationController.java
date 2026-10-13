package com.mebelsex.ui;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.Messages;
import com.mebelsex.license.LicenseService;
import com.mebelsex.license.LicenseStatus;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * The activation window shown when the app is BLOCKED: it shows this machine's
 * code, the reason and a file chooser to import a license. On success the caller
 * is notified so the main window can be built in the same process (no restart).
 */
@Controller
@Scope("prototype")
public class ActivationController {

    private final LicenseService licenseService;
    private final ResourceBundle bundle;

    private Consumer<LicenseStatus> onImported;

    @FXML
    private TextField machineField;
    @FXML
    private Button copyButton;
    @FXML
    private Label copiedLabel;
    @FXML
    private Button chooseButton;
    @FXML
    private Button exitButton;
    @FXML
    private Label errorLabel;

    public ActivationController(LicenseService licenseService) {
        this.licenseService = licenseService;
        this.bundle = Messages.bundle();
    }

    public void setOnImported(Consumer<LicenseStatus> onImported) {
        this.onImported = onImported;
    }

    @FXML
    public void initialize() {
        machineField.setText(licenseService.machineId().orElse(""));
        LicenseStatus status = licenseService.current();
        String reasonKey = status.reasonKey() == null ? "license.reason.missing" : status.reasonKey();
        errorLabel.setText(bundle.getString(reasonKey));
    }

    @FXML
    public void handleCopy() {
        String machine = machineField.getText();
        if (machine == null || machine.isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(machine);
        Clipboard.getSystemClipboard().setContent(content);
        copiedLabel.setManaged(true);
        copiedLabel.setVisible(true);
    }

    @FXML
    public void handleExit() {
        javafx.application.Platform.exit();
    }

    @FXML
    public void handleChoose() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(bundle.getString("license.activation.choose.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                bundle.getString("license.activation.filter"), "*.lic", "*.LIC"));
        File chosen = chooser.showOpenDialog(window());
        if (chosen == null) {
            return;
        }

        chooseButton.setDisable(true);
        copiedLabel.setManaged(false);
        copiedLabel.setVisible(false);
        errorLabel.setText("");

        Task<LicenseStatus> task = new Task<>() {
            @Override
            protected LicenseStatus call() {
                return licenseService.importLicense(chosen.toPath());
            }
        };
        task.setOnSucceeded(event -> {
            chooseButton.setDisable(false);
            if (onImported != null) {
                onImported.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            chooseButton.setDisable(false);
            Throwable failure = task.getException();
            BusinessException business = ExceptionHandler.findBusinessException(failure);
            String key = business == null ? "ui.unexpected_error" : business.getMessageKey();
            errorLabel.setText(bundle.getString(key));
        });

        Thread thread = new Thread(task, "license-import");
        thread.setDaemon(true);
        thread.start();
    }

    private Window window() {
        return copyButton == null || copyButton.getScene() == null ? null : copyButton.getScene().getWindow();
    }
}
