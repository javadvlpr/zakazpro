package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that visible text in {@code com.mebelsex.ui} comes from message keys, not from
 * inline literals. Empty strings (layout placeholders) are allowed. The dev-only snapshot
 * tool and the pre-Spring recovery dialogs are excluded (they load keys through the bundle
 * directly and are not part of the normal UI flow).
 */
class UiStringsTest {

    private static final Set<String> EXCLUDED = Set.of("SnapshotConfigurator.java", "ImageDiffTool.java");

    private static final Pattern[] LITERALS = {
            Pattern.compile("\\.(?:setTitle|setHeaderText|setContentText|setPromptText)\\(\\s*\"([^\"]*)\""),
            Pattern.compile("new\\s+(?:Label|Button)\\(\\s*\"([^\"]*)\"")
    };

    @Test
    void visibleTextUsesMessageKeys() throws Exception {
        Path root = Paths.get("src/main/java/com/mebelsex/ui");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (EXCLUDED.contains(file.getFileName().toString())) {
                    continue;
                }
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (Pattern pattern : LITERALS) {
                    Matcher matcher = pattern.matcher(content);
                    while (matcher.find()) {
                        String literal = matcher.group(1);
                        if (!literal.isEmpty()) {
                            offenders.add(root.relativize(file) + " -> \"" + literal + "\"");
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "hardcoded UI strings (use message keys): " + offenders);
    }
}
