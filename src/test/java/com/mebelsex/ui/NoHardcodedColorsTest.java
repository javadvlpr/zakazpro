package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the colour token system: colours must live only in the .root / .root.dark
 * token blocks of application.css and be referenced elsewhere as -mx-* tokens or
 * style classes.
 *
 * Allow-list (documented, intentionally tiny): neutral-black drop shadows,
 * {@code rgba(0, 0, 0, <alpha>)}, which read fine in both themes. {@code transparent}
 * is a keyword, not a colour literal, and stays allowed.
 */
class NoHardcodedColorsTest {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern RGB = Pattern.compile("rgba?\\(");
    private static final Pattern NEUTRAL_SHADOW = Pattern.compile("rgba\\(\\s*0\\s*,\\s*0\\s*,\\s*0\\s*,\\s*[0-9.]+\\s*\\)");
    private static final Pattern COLOR_CONSTRUCTOR = Pattern.compile("\\bColor\\.[A-Za-z]");
    private static final Pattern STYLE_COLOR_LITERAL = Pattern.compile("-fx-[a-z-]*color\\s*:\\s*(?!-)");

    @Test
    void applicationCssUsesTokensOutsideTheRootBlocks() throws Exception {
        Path cssFile = Paths.get("src/main/resources/css/application.css");
        String css = Files.readString(cssFile);
        // Drop the two token-definition blocks; colours are allowed only there.
        String scannable = css.replaceAll("\\.root(?:\\.dark)?\\s*\\{[^}]*\\}", "");
        scannable = NEUTRAL_SHADOW.matcher(scannable).replaceAll("");
        List<String> offenders = new ArrayList<>();
        collect(HEX, scannable, cssFile, offenders);
        collect(RGB, scannable, cssFile, offenders);
        assertTrue(offenders.isEmpty(), "application.css must use -mx-* tokens outside the .root blocks: " + offenders);
    }

    @Test
    void fxmlFilesHaveNoInlineColours() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/resources/fxml"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).collect(Collectors.toList())) {
                String content = Files.readString(file);
                collect(HEX, content, file, offenders);
                collect(RGB, content, file, offenders);
            }
        }
        assertTrue(offenders.isEmpty(), "FXML must use style classes, not inline colours: " + offenders);
    }

    @Test
    void uiJavaHasNoHardcodedColours() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java/com/mebelsex/ui"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String content = Files.readString(file);
                collect(HEX, content, file, offenders);
                collect(RGB, content, file, offenders);
                collect(COLOR_CONSTRUCTOR, content, file, offenders);
                collect(STYLE_COLOR_LITERAL, content, file, offenders);
            }
        }
        assertTrue(offenders.isEmpty(),
                "UI Java must use style classes / -mx tokens, not inline colours: " + offenders);
    }

    private static void collect(Pattern pattern, String content, Path file, List<String> offenders) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            offenders.add(file + " -> " + matcher.group());
        }
    }
}
