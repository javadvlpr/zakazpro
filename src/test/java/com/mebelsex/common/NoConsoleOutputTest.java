package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that application code logs through SLF4J instead of the console.
 * The two dev-only snapshot tools keep their console output on purpose (they are
 * never used in production and are driven from the terminal), and the build-time
 * release tools route all of it through the single allow-listed ConsoleOutput class
 * (they run from a .cmd where logback is not on the classpath).
 */
class NoConsoleOutputTest {

    private static final Set<String> DEV_ONLY = Set.of(
            "SnapshotConfigurator.java", "ImageDiffTool.java", "ConsoleOutput.java");

    @Test
    void noConsoleOutputOrPrintStackTraceUnderSrcMain() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path root = Paths.get("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (DEV_ONLY.contains(file.getFileName().toString())) {
                    continue;
                }
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (text.contains("System.out.print") || text.contains("System.err.print")
                        || text.contains("printStackTrace(")) {
                    offenders.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "use SLF4J instead of console output in: " + offenders);
    }
}
