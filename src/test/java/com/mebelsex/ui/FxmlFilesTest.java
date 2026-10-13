package com.mebelsex.ui;

import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static checks for every FXML file. Catches the failures that otherwise show up only at runtime:
 * a broken XML declaration, a missing controller class, a missing onAction handler, or an @FXML field
 * in the controller that has no matching fx:id in the FXML (it would stay null and crash initialize()).
 *
 * It also checks that the controller field TYPE can hold the FXML element (a TextField cannot be injected into a
 * SearchField field) and that only the installed Ikonli pack (Feather, "fth-") is used.
 *
 * Note: an fx:id without a controller field is allowed by JavaFX, so it is not checked.
 */
public class FxmlFilesTest {

    private static final String FXML_NS = "http://javafx.com/fxml/1";

    @Test
    public void allFxmlFilesAreValidAndMatchTheirControllers() throws Exception {
        Path root = Paths.get("src/main/resources/fxml");
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(p -> p.toString().endsWith(".fxml")).sorted().collect(Collectors.toList());
        }
        assertFalse(files.isEmpty(), "No FXML files found under " + root);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

        for (Path fxml : files) {
            List<String> lines = Files.readAllLines(fxml, StandardCharsets.UTF_8);
            String firstLine = lines.isEmpty() ? "" : lines.get(0).trim();
            assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", firstLine,
                    "Invalid XML declaration in " + fxml);

            String controllerName = null;
            Set<String> fxIds = new HashSet<>();
            Map<String, String> idToTag = new LinkedHashMap<>();
            List<String> actions = new ArrayList<>();

            try (InputStream is = Files.newInputStream(fxml)) {
                XMLStreamReader reader = factory.createXMLStreamReader(is);
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String tag = reader.getLocalName();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String uri = reader.getAttributeNamespace(i);
                        String name = reader.getAttributeLocalName(i);
                        String value = reader.getAttributeValue(i);
                        if (FXML_NS.equals(uri) && "controller".equals(name)) {
                            controllerName = value;
                        } else if (FXML_NS.equals(uri) && "id".equals(name)) {
                            fxIds.add(value);
                            idToTag.put(value, tag);
                        } else if ("onAction".equals(name) && value.startsWith("#")) {
                            actions.add(value.substring(1));
                        }
                    }
                }
            } catch (Exception e) {
                fail("StAX parse failed for " + fxml + ": " + e.getMessage());
            }

            if (controllerName == null) {
                continue;
            }

            Class<?> controller = Class.forName(controllerName, false, getClass().getClassLoader());

            for (String action : actions) {
                assertTrue(hasMethod(controller, action),
                        "Method " + action + " not found in " + controllerName + " (used in " + fxml + ")");
            }

            List<String> imports = readImports(lines);
            for (Class<?> c = controller; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(FXML.class)) {
                        assertTrue(fxIds.contains(field.getName()),
                                "@FXML field " + field.getName() + " in " + controllerName
                                        + " has no fx:id in " + fxml);
                        Class<?> element = resolveTag(idToTag.get(field.getName()), imports);
                        if (element != null) {
                            assertTrue(field.getType().isAssignableFrom(element),
                                    "Type mismatch in " + fxml + ": fx:id " + field.getName() + " is <"
                                            + element.getSimpleName() + "> but " + controllerName + " declares "
                                            + field.getType().getSimpleName());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void onlyInstalledIconPackIsUsed() throws Exception {
        try (Stream<Path> stream = Files.walk(Paths.get("src/main/resources/fxml"))) {
            for (Path fxml : stream.filter(p -> p.toString().endsWith(".fxml")).collect(Collectors.toList())) {
                Matcher m = Pattern.compile("iconLiteral=\"([^\"]+)\"")
                        .matcher(new String(Files.readAllBytes(fxml), StandardCharsets.UTF_8));
                while (m.find()) {
                    assertTrue(m.group(1).startsWith("fth-"),
                            "Icon '" + m.group(1) + "' in " + fxml + " is not from the installed Feather pack (fth-)");
                }
            }
        }
    }

    @Test
    public void noFxmlUsesInfinityAsPrefSize() throws Exception {
        try (Stream<Path> stream = Files.walk(Paths.get("src/main/resources/fxml"))) {
            for (Path fxml : stream.filter(p -> p.toString().endsWith(".fxml")).collect(Collectors.toList())) {
                String content = new String(Files.readAllBytes(fxml), StandardCharsets.UTF_8);
                assertFalse(content.contains("prefWidth=\"Infinity\""),
                        "prefWidth=\"Infinity\" breaks sizing in " + fxml + "; use maxWidth + hgrow instead");
                assertFalse(content.contains("prefHeight=\"Infinity\""),
                        "prefHeight=\"Infinity\" breaks sizing in " + fxml + "; use maxHeight + vgrow instead");
            }
        }
    }

    private static List<String> readImports(List<String> lines) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("<\\?import\\s+([^\\s?]+)\\s*\\?>");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                result.add(m.group(1));
            }
        }
        return result;
    }

    private static Class<?> resolveTag(String tag, List<String> imports) {
        if (tag == null) {
            return null;
        }
        for (String imp : imports) {
            String name = imp.endsWith(".*") ? imp.substring(0, imp.length() - 1) + tag
                    : (imp.endsWith("." + tag) ? imp : null);
            if (name == null) {
                continue;
            }
            try {
                return Class.forName(name, false, FxmlFilesTest.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next import
            }
        }
        return null;
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
