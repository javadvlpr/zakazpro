package com.mebelsex.release;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * The installer settings kept in one place ({@code installer/installer.properties}): the
 * visible identity, the stable upgrade UUID, the JVM options of the packaged app, the extra
 * JDK modules and the icon paths.
 *
 * <p>
 * {@link #parse(Map)} is pure, so the rules (valid upgrade UUID, no license dev option) are
 * unit-tested without touching the file system.
 */
public record InstallerConfig(
        String appName,
        String vendor,
        String description,
        String copyright,
        String menuGroup,
        String upgradeUuid,
        List<String> javaOptions,
        List<String> extraModules,
        String iconPath,
        String iconSource) {

    /** Default location, relative to the project root. */
    public static final String DEFAULT_FILE = "installer/installer.properties";

    /** The nil UUID is never a real upgrade id. */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    /** Must never reach the packaged application. */
    static final String FORBIDDEN_OPTION_PREFIX = "mebelsex.license";

    private static final String KEY_NAME = "app.name";
    private static final String KEY_VENDOR = "app.vendor";
    private static final String KEY_DESCRIPTION = "app.description";
    private static final String KEY_COPYRIGHT = "app.copyright";
    private static final String KEY_MENU_GROUP = "app.menu.group";
    private static final String KEY_UPGRADE_UUID = "app.upgrade.uuid";
    private static final String KEY_JVM_OPTIONS = "app.jvm.options";
    private static final String KEY_EXTRA_MODULES = "jdk.extra.modules";
    private static final String KEY_ICON_PATH = "icon.path";
    private static final String KEY_ICON_SOURCE = "icon.source";

    /** Loads the file (UTF-8). */
    public static InstallerConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Installer configuration not found: " + file);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return parse(values);
    }

    /** Builds the configuration from raw properties (missing keys become empty). */
    public static InstallerConfig parse(Map<String, String> values) {
        return new InstallerConfig(
                text(values, KEY_NAME),
                text(values, KEY_VENDOR),
                text(values, KEY_DESCRIPTION),
                text(values, KEY_COPYRIGHT),
                text(values, KEY_MENU_GROUP),
                text(values, KEY_UPGRADE_UUID),
                split(text(values, KEY_JVM_OPTIONS)),
                split(text(values, KEY_EXTRA_MODULES)),
                text(values, KEY_ICON_PATH),
                text(values, KEY_ICON_SOURCE));
    }

    /** Every problem that would make the build wrong; empty when the file is usable. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        require(problems, appName, KEY_NAME);
        require(problems, vendor, KEY_VENDOR);
        require(problems, description, KEY_DESCRIPTION);
        require(problems, copyright, KEY_COPYRIGHT);
        require(problems, menuGroup, KEY_MENU_GROUP);
        require(problems, iconPath, KEY_ICON_PATH);
        require(problems, iconSource, KEY_ICON_SOURCE);
        if (javaOptions.isEmpty()) {
            problems.add(KEY_JVM_OPTIONS + " must contain at least one option");
        }
        for (String option : javaOptions) {
            if (option.contains(FORBIDDEN_OPTION_PREFIX)) {
                problems.add(KEY_JVM_OPTIONS + " must never contain " + FORBIDDEN_OPTION_PREFIX
                        + " (found: " + option + ")");
            }
            if (!option.startsWith("-")) {
                problems.add(KEY_JVM_OPTIONS + " entry is not an option: " + option);
            }
        }
        for (String module : extraModules) {
            if (!module.matches("(java|jdk)[.][a-zA-Z0-9._]+")) {
                problems.add(KEY_EXTRA_MODULES + " entry is not a JDK module name: " + module);
            }
        }
        problems.addAll(upgradeUuidProblems());
        return problems;
    }

    public boolean valid() {
        return problems().isEmpty();
    }

    /** The upgrade UUID, or null when it is missing/invalid. */
    public UUID upgradeUuidOrNull() {
        try {
            UUID parsed = UUID.fromString(upgradeUuid.trim());
            return NIL_UUID.equals(parsed) ? null : parsed;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> upgradeUuidProblems() {
        if (upgradeUuid.isBlank()) {
            return List.of(KEY_UPGRADE_UUID + " is required (generate it once with UUID.randomUUID)");
        }
        if (upgradeUuidOrNull() == null) {
            return List.of(KEY_UPGRADE_UUID + " must be a real UUID, not '" + upgradeUuid + "'");
        }
        return List.of();
    }

    private static void require(List<String> problems, String value, String key) {
        if (value.isBlank()) {
            problems.add(key + " is required");
        }
    }

    private static String text(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    private static List<String> split(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
