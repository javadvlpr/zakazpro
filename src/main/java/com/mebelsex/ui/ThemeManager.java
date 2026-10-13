package com.mebelsex.ui;

import com.mebelsex.settings.SettingsService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Holds the active {@link Theme}, applies it to every open window/dialog and persists the
 * choice to the settings table. The dark theme is a {@code dark} style class on the scene
 * root (or the {@link DialogPane} itself for alerts); application.css holds the palette.
 *
 * No business logic. Registered as a Spring bean; {@link #getInstance()} lets static
 * helpers (AlertHelper, dialog builders) reach it without constructor churn.
 */
@Component
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private static final String DARK_CLASS = "dark";
    private static final String CSS = "/css/application.css";

    private static volatile ThemeManager instance;

    private final SettingsService settingsService;
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);
    private final List<WeakReference<Parent>> roots = new ArrayList<>();

    public ThemeManager(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.theme.addListener((obs, old, now) -> applyToAll());
        instance = this;
        AlertHelper.setThemeManager(this);
    }

    /** The most recently created manager, or null before Spring has started. */
    public static ThemeManager getInstance() {
        return instance;
    }

    /** Observable current theme (the Sozlamalar toggles bind to it). */
    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme current() {
        return theme.get();
    }

    /**
     * Adds application.css to the scene (if missing) and applies the current theme,
     * registering the scene root so later theme changes reach this window.
     */
    public void install(Scene scene) {
        if (scene == null) {
            return;
        }
        ensureStylesheet(scene);
        AppIcons.install(scene);
        Parent root = scene.getRoot();
        if (root != null) {
            register(root);
        }
    }

    /** Applies the theme to a dialog pane (Alerts, TextInputDialog, ProgressDialog). */
    public void install(DialogPane pane) {
        if (pane == null) {
            return;
        }
        register(pane);
        AppIcons.install(pane);
        Scene scene = pane.getScene();
        if (scene != null) {
            ensureStylesheet(scene);
        } else {
            pane.sceneProperty().addListener((obs, old, now) -> {
                if (now != null) {
                    ensureStylesheet(now);
                }
            });
        }
    }

    /** Switches theme live for every registered window/dialog and persists the choice. */
    public void setTheme(Theme next) {
        Theme chosen = next == null ? Theme.LIGHT : next;
        if (theme.get() == chosen) {
            persist(chosen);
            return;
        }
        if (Platform.isFxApplicationThread()) {
            theme.set(chosen);
        } else {
            Platform.runLater(() -> theme.set(chosen));
        }
        persist(chosen);
    }

    /** Reads {@code ui.theme} from settings; call before the main window is shown. */
    public void loadFromSettings() {
        // Dev-only override for the snapshot tool; never touches the stored setting.
        String devOverride = System.getenv("MEBELSEX_THEME");
        if (devOverride != null && !devOverride.isBlank()) {
            theme.set(Theme.parse(devOverride));
            return;
        }
        theme.set(Theme.parse(settingsService.loadTheme()));
    }

    // ------------------------------------------------------------------

    private void register(Parent node) {
        apply(node);
        synchronized (roots) {
            roots.add(new WeakReference<>(node));
        }
    }

    private void apply(Parent node) {
        boolean dark = theme.get() == Theme.DARK;
        if (dark) {
            if (!node.getStyleClass().contains(DARK_CLASS)) {
                node.getStyleClass().add(DARK_CLASS);
            }
        } else {
            node.getStyleClass().remove(DARK_CLASS);
        }
    }

    private void applyToAll() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::applyToAll);
            return;
        }
        synchronized (roots) {
            Iterator<WeakReference<Parent>> it = roots.iterator();
            while (it.hasNext()) {
                Parent node = it.next().get();
                if (node == null) {
                    it.remove();
                } else {
                    apply(node);
                }
            }
        }
    }

    private void ensureStylesheet(Scene scene) {
        String css = ThemeManager.class.getResource(CSS).toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    private void persist(Theme chosen) {
        Thread saver = new Thread(() -> {
            try {
                settingsService.saveTheme(chosen.name());
            } catch (Exception e) {
                log.warn("Could not persist ui.theme", e);
            }
        }, "theme-save");
        saver.setDaemon(true);
        saver.start();
    }
}
