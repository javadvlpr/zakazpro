package com.mebelsex.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets the application icon (window and taskbar) from the PNGs in {@code /icons/}.
 *
 * <p>
 * {@link ThemeManager} installs the icons on every scene and dialog pane it themes, so the
 * main window and every alert get them for free. The few windows that exist before Spring
 * (splash, already-running, recovery, startup failure) call {@link #apply(Stage)} or
 * {@link #install(Scene)} directly.
 *
 * <p>
 * Loading is lazy and cached; a missing resource is logged, never fatal.
 */
public final class AppIcons {

    private static final Logger log = LoggerFactory.getLogger(AppIcons.class);

    /** Largest first, so Windows can pick the best size for the context. */
    private static final String[] RESOURCES = {
            "/icons/app-256.png", "/icons/app-64.png", "/icons/app-32.png"
    };

    private static List<Image> cache;

    private AppIcons() {
    }

    /** The decoded icon images (empty when none of the resources is on the classpath). */
    public static List<Image> images() {
        synchronized (AppIcons.class) {
            if (cache == null) {
                List<Image> loaded = new ArrayList<>();
                for (String resource : RESOURCES) {
                    URL url = AppIcons.class.getResource(resource);
                    if (url == null) {
                        log.warn("Icon resource missing: {}", resource);
                        continue;
                    }
                    loaded.add(new Image(url.toExternalForm()));
                }
                cache = List.copyOf(loaded);
            }
            return cache;
        }
    }

    /**
     * Sets the icons as soon as the scene has a window (now or later). Safe to call before
     * the scene is attached.
     */
    public static void install(Scene scene) {
        if (scene == null) {
            return;
        }
        if (scene.getWindow() instanceof Stage stage) {
            apply(stage);
            return;
        }
        scene.windowProperty().addListener((obs, old, now) -> {
            if (now instanceof Stage stage) {
                apply(stage);
            }
        });
    }

    /** Waits for the dialog pane's own scene, then sets the icons on its window. */
    public static void install(DialogPane pane) {
        if (pane == null) {
            return;
        }
        Scene scene = pane.getScene();
        if (scene != null) {
            install(scene);
        } else {
            pane.sceneProperty().addListener((obs, old, now) -> install(now));
        }
    }

    /** Sets the icons on a stage that has none yet. */
    public static void apply(Stage stage) {
        if (stage == null || !stage.getIcons().isEmpty()) {
            return;
        }
        try {
            List<Image> images = images();
            if (!images.isEmpty()) {
                stage.getIcons().setAll(images);
            }
        } catch (RuntimeException e) {
            log.warn("Could not set the window icons: {}", e.getMessage());
        }
    }
}
