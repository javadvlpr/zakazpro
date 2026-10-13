package com.mebelsex.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.stage.Window;

public class AlertHelper {

    private static final ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
    // Exposed so tests can assert the ButtonData without a JavaFX toolkit.
    public static final ButtonType YES_BUTTON = new ButtonType(bundle.getString("ui.yes"),
            ButtonBar.ButtonData.YES);
    public static final ButtonType NO_BUTTON = new ButtonType(bundle.getString("ui.no"),
            ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final ButtonType CANCEL_BUTTON = new ButtonType(bundle.getString("ui.cancel"));
    private static final ButtonType OK_BUTTON = new ButtonType(bundle.getString("ui.ok"));

    /** Set by ThemeManager so every alert follows the active theme. */
    private static volatile ThemeManager themeManager;

    public static void setThemeManager(ThemeManager manager) {
        themeManager = manager;
    }

    public static Window getActiveWindow(List<Window> windows) {
        return windows.stream()
                .filter(w -> w != null && w.isShowing())
                .findFirst()
                .orElse(null);
    }

    public static void info(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(OK_BUTTON);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Window owner = getActiveWindow(Window.getWindows());
        if (owner != null) {
            alert.initOwner(owner);
        }
        applyStyle(alert);
        alert.showAndWait();
    }

    public static void error(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(OK_BUTTON);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Window owner = getActiveWindow(Window.getWindows());
        if (owner != null) {
            alert.initOwner(owner);
        }
        applyStyle(alert);
        alert.showAndWait();
    }

    public static boolean confirm(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(YES_BUTTON, NO_BUTTON);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Window owner = getActiveWindow(Window.getWindows());
        if (owner != null) {
            alert.initOwner(owner);
        }
        applyStyle(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == YES_BUTTON;
    }

    private static void applyStyle(Alert alert) {
        if (alert.getDialogPane().getScene().getStylesheets().isEmpty()) {
            String css = AlertHelper.class.getResource("/css/application.css").toExternalForm();
            alert.getDialogPane().getScene().getStylesheets().add(css);
        }
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        if (themeManager != null) {
            themeManager.install(alert.getDialogPane());
        }
    }
}
