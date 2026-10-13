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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the visible rebrand: the OLD visible names must not appear in the message bundle,
 * FXML/CSS/XML, Java string literals under src/main or the pom name.
 *
 * <p>
 * A small allow-list covers INTERNAL identifiers that must keep the old spelling because
 * renaming them would break existing installs/licenses:
 * <ul>
 * <li>{@code BackupService.java} / {@code RecoveryApplication.java}: the legacy {@code mebel_}
 * prefix is kept only to recognise old backup files.</li>
 * <li>{@code PendingRestoreApplier.java}: restore/replace marker names inside the data folder
 * are internal and never shown to the user.</li>
 * </ul>
 * SQL migrations are not scanned (V1 is immutable and its header comment must not change).
 */
class ProductNameTest {

    /** Old VISIBLE names and file/folder names. */
    private static final Pattern OLD_NAMES = Pattern.compile(
            "Mebel Sex|MebelSex|mebel_|mebelsex\\.log(?![.\\w])|mebel-diagnostics|mebel-logs");

    private static final Set<String> ALLOWED_FILES = Set.of(
            "BackupService.java", "PendingRestoreApplier.java", "RecoveryApplication.java");

    private static final Set<String> RESOURCE_EXTENSIONS = Set.of(".fxml", ".css", ".properties", ".xml");

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void noOldVisibleNameInSourcesOrResources() throws Exception {
        List<String> offenders = new ArrayList<>();
        scanJavaLiterals(Paths.get("src/main/java"), offenders);
        scanResources(Paths.get("src/main/resources"), offenders);
        scanWholeFile(Paths.get("pom.xml"), "pom.xml", offenders);
        assertTrue(offenders.isEmpty(), "old visible product name found: " + offenders);
    }

    private static void scanJavaLiterals(Path root, List<String> offenders) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (ALLOWED_FILES.contains(file.getFileName().toString())) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = STRING_LITERAL.matcher(text);
                while (matcher.find()) {
                    check(matcher.group(1), root.relativize(file).toString(), offenders);
                }
            }
        }
    }

    private static void scanResources(Path root, List<String> offenders) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                boolean scanned = RESOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
                if (scanned) {
                    scanWholeFile(file, root.relativize(file).toString(), offenders);
                }
            }
        }
    }

    private static void scanWholeFile(Path file, String label, List<String> offenders) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        check(Files.readString(file, StandardCharsets.UTF_8), label, offenders);
    }

    private static void check(String text, String label, List<String> offenders) {
        Matcher matcher = OLD_NAMES.matcher(text);
        while (matcher.find()) {
            offenders.add(label + " -> \"" + matcher.group() + "\"");
        }
    }
}
