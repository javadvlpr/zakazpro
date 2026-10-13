package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring: a class that builds a JavaFX {@code Scene} must install the theme through
 * {@link ThemeManager}, otherwise the dark theme never reaches that window. AlertHelper must
 * apply it to its DialogPane. This is a source scan (no JavaFX toolkit).
 */
class ThemeWiringTest {

    @Test
    void everySceneBuilderReferencesThemeManager() throws Exception {
        Path src = Paths.get("src/main/java/com/mebelsex");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(src)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".java")).collect(Collectors.toList())) {
                if ("ThemeManager.java".equals(p.getFileName().toString())) {
                    continue;
                }
                String content = Files.readString(p);
                if (content.contains("new Scene(") && !content.contains("ThemeManager")) {
                    offenders.add(p.toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Classes that build a Scene must install the theme via ThemeManager: " + offenders);
    }

    @Test
    void alertHelperUsesThemeManager() throws Exception {
        String content = Files.readString(Paths.get("src/main/java/com/mebelsex/ui/AlertHelper.java"));
        assertTrue(content.contains("ThemeManager"),
                "AlertHelper must apply the active theme to every alert DialogPane");
    }
}
