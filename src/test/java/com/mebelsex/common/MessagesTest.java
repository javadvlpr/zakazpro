package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @Test
    void getReturnsExistingValueAndKeyForMissing() {
        assertFalse(Messages.get("ui.error").isBlank());
        assertEquals("does.not.exist", Messages.get("does.not.exist"));
    }

    @Test
    void formatAppliesMessageFormatArguments() {
        String formatted = Messages.format("orders.total_debt", "1 000 so'm");
        assertTrue(formatted.contains("1 000 so'm"), formatted);
    }

    @Test
    void resourceBundleGetBundleAppearsOnlyInsideMessages() throws Exception {
        Path root = Paths.get("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                if ("Messages.java".equals(file.getFileName().toString())) {
                    continue;
                }
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (content.contains("ResourceBundle.getBundle(")) {
                    offenders.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "message bundles must be loaded only through com.mebelsex.common.Messages: " + offenders);
    }
}
