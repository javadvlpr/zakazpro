package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the UI layer against lazy-loading bugs: controllers must use read-models
 * (DTOs/records) instead of the JPA entities Order and Payment, whose associations
 * are lazy and cannot be initialized on the JavaFX thread.
 */
class UiDoesNotImportEntityAssociationsTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.mebelsex.orders.Order;",
            "import com.mebelsex.payments.Payment;");

    @Test
    void uiPackageDoesNotImportLazyEntityClasses() throws IOException {
        Path root = Paths.get("src/main/java/com/mebelsex/ui");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (FORBIDDEN_IMPORTS.contains(trimmed)) {
                        offenders.add(file + " -> " + trimmed);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "UI classes must use DTOs, not lazy entities (Order/Payment): " + offenders);
    }
}
