package com.mebelsex.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class I18NValidationTest {

    @Test
    void testAllFxmlKeysExistInBundle() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_uz");
        Set<String> missingKeys = new HashSet<>();

        Pattern fxmlKeyPattern = Pattern.compile("text=\"%([^\"]+)\"");
        Pattern fxmlPromptPattern = Pattern.compile("promptText=\"%([^\"]+)\"");

        Path fxmlDir = Paths.get("src/main/resources/fxml");
        try (Stream<Path> paths = Files.walk(fxmlDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fxml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Matcher mText = fxmlKeyPattern.matcher(content);
                            while (mText.find()) {
                                String key = mText.group(1);
                                if (!bundle.containsKey(key))
                                    missingKeys.add(key);
                            }
                            Matcher mPrompt = fxmlPromptPattern.matcher(content);
                            while (mPrompt.find()) {
                                String key = mPrompt.group(1);
                                if (!bundle.containsKey(key))
                                    missingKeys.add(key);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertTrue(missingKeys.isEmpty(), "Missing translation keys in FXMLs: " + missingKeys);
    }

    @Test
    void testFileHasNoNulAndNoDuplicates() throws Exception {
        Path propsFile = Paths.get("src/main/resources/i18n/messages_uz.properties");
        byte[] bytes = Files.readAllBytes(propsFile);
        for (byte b : bytes) {
            if (b == 0) {
                fail("messages_uz.properties contains NUL character!");
            }
        }

        List<String> lines = Files.readAllLines(propsFile);
        Set<String> keys = new HashSet<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            int idx = line.indexOf('=');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                assertFalse(keys.contains(key), "Duplicate key found: " + key);
                keys.add(key);
            }
        }
    }

    @Test
    void testBusinessExceptionAndConstantsKeys() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_uz");
        Set<String> missingKeys = new HashSet<>();

        Pattern exPattern = Pattern.compile("new\\s+BusinessException\\(\\s*\"([a-zA-Z0-9_.]+)\"");
        Pattern bundlePattern = Pattern.compile("bundle\\.getString\\(\\s*\"([a-zA-Z0-9_.]+)\"");

        Path srcMain = Paths.get("src/main/java");
        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Matcher mEx = exPattern.matcher(content);
                            while (mEx.find()) {
                                String key = mEx.group(1);
                                if (!bundle.containsKey(key))
                                    missingKeys.add(key);
                            }
                            Matcher mBundle = bundlePattern.matcher(content);
                            while (mBundle.find()) {
                                String key = mBundle.group(1);
                                if (key.endsWith("."))
                                    continue; // dynamic concatenated keys
                                if (!bundle.containsKey(key))
                                    missingKeys.add(key);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        assertTrue(missingKeys.isEmpty(), "Missing message keys in Java code: " + missingKeys);
    }

    @Test
    void testNoPlusPrefixOrSampleText() throws Exception {
        Path propsFile = Paths.get("src/main/resources/i18n/messages_uz.properties");
        List<String> lines = Files.readAllLines(propsFile);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;
            int idx = trimmed.indexOf('=');
            if (idx < 0)
                continue;
            String value = trimmed.substring(idx + 1).trim();
            assertFalse(value.startsWith("+"), "Message value starts with '+': " + trimmed);
            assertFalse(value.contains("Sample"), "Message value contains 'Sample': " + trimmed);
            assertFalse(value.contains("(Advance)"), "Message value contains '(Advance)': " + trimmed);
        }
    }

    @Test
    void testApostropheEncoding() throws Exception {
        Path propsFile = Paths.get("src/main/resources/i18n/messages_uz.properties");
        List<String> lines = Files.readAllLines(propsFile);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            int idx = line.indexOf('=');
            if (idx > 0) {
                String val = line.substring(idx + 1).trim();
                if (!val.matches(".*\\{[0-9]+\\}.*")) {
                    // doesn't have {n} format argument, so double apostrophe is
                    // unnecessary/erroneous
                    assertFalse(val.contains("''"), "Line contains double apostrophe without format args: " + line);
                }
            }
        }
    }
}
