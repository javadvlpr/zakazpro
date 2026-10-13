package com.mebelsex.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for installer/installer.properties. */
class InstallerConfigTest {

    private static final String UUID_TEXT = "c3138c4d-c7aa-4b27-a524-eb2a1c59d38e";

    private static Map<String, String> validProperties() {
        Map<String, String> values = new HashMap<>();
        values.put("app.name", "ZakazPro");
        values.put("app.vendor", "ZakazPro");
        values.put("app.description", "Buyurtma va sex hisobini yuritish dasturi");
        values.put("app.copyright", "(c) 2026 ZakazPro");
        values.put("app.menu.group", "ZakazPro");
        values.put("app.upgrade.uuid", UUID_TEXT);
        values.put("app.jvm.options", "-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xmx768m -XX:+UseSerialGC");
        values.put("jdk.extra.modules", "");
        values.put("icon.path", "installer/assets/zakazpro.ico");
        values.put("icon.source", "installer/assets/zakazpro-icon.png");
        return values;
    }

    @Test
    void parsesTheJvmOptionsIntoOneEntryPerOption() {
        InstallerConfig config = InstallerConfig.parse(validProperties());

        assertEquals(4, config.javaOptions().size());
        assertEquals("-Dfile.encoding=UTF-8", config.javaOptions().get(0));
        assertEquals("-XX:+UseSerialGC", config.javaOptions().get(3));
        assertTrue(config.extraModules().isEmpty());
        assertTrue(config.valid(), () -> config.problems().toString());
    }

    @Test
    void theShippedFileIsValid() throws Exception {
        InstallerConfig config = InstallerConfig.load(Paths.get(InstallerConfig.DEFAULT_FILE));

        assertTrue(config.valid(), () -> config.problems().toString());
        assertEquals("ZakazPro", config.appName());
        assertEquals("ZakazPro", config.menuGroup());
        assertNotNull(config.upgradeUuidOrNull(), "the upgrade UUID must be a real UUID");
        assertFalse(config.upgradeUuidOrNull().equals(InstallerConfig.NIL_UUID));
        for (String option : config.javaOptions()) {
            assertFalse(option.contains("mebelsex.license"), "the license flag must never reach the installer");
        }
        assertTrue(config.iconPath().endsWith(".ico"), config.iconPath());
    }

    @Test
    void rejectsMissingIdentityValues() {
        Map<String, String> values = validProperties();
        values.remove("app.name");
        values.remove("app.description");

        InstallerConfig config = InstallerConfig.parse(values);

        assertFalse(config.valid());
        assertTrue(config.problems().toString().contains("app.name"));
        assertTrue(config.problems().toString().contains("app.description"));
    }

    @Test
    void rejectsAPlaceholderOrMissingUpgradeUuid() {
        Map<String, String> placeholder = validProperties();
        placeholder.put("app.upgrade.uuid", "00000000-0000-0000-0000-000000000000");
        assertFalse(InstallerConfig.parse(placeholder).valid());
        assertNull(InstallerConfig.parse(placeholder).upgradeUuidOrNull());

        Map<String, String> garbage = validProperties();
        garbage.put("app.upgrade.uuid", "REPLACE-ME");
        assertFalse(InstallerConfig.parse(garbage).valid());
        assertNull(InstallerConfig.parse(garbage).upgradeUuidOrNull());

        Map<String, String> empty = validProperties();
        empty.put("app.upgrade.uuid", "");
        assertFalse(InstallerConfig.parse(empty).valid());
    }

    @Test
    void acceptsAFreshRandomUuid() {
        assertEquals(UUID_TEXT, InstallerConfig.parse(validProperties()).upgradeUuidOrNull().toString());
        Map<String, String> values = validProperties();
        values.put("app.upgrade.uuid", UUID.randomUUID().toString());
        assertTrue(InstallerConfig.parse(values).valid());
    }

    @Test
    void rejectsALicenseDeveloperOption() {
        Map<String, String> values = validProperties();
        values.put("app.jvm.options", "-Xmx768m -Dmebelsex.license.dev=true");

        InstallerConfig config = InstallerConfig.parse(values);

        assertFalse(config.valid());
        assertTrue(config.problems().toString().contains("mebelsex.license"), config.problems().toString());
    }

    @Test
    void rejectsAnEmptyOptionListAndANonOption() {
        Map<String, String> empty = validProperties();
        empty.put("app.jvm.options", "   ");
        assertFalse(InstallerConfig.parse(empty).valid());

        Map<String, String> nonOption = validProperties();
        nonOption.put("app.jvm.options", "-Xmx768m 768m");
        assertFalse(InstallerConfig.parse(nonOption).valid());
    }

    @Test
    void rejectsABadExtraModuleName() {
        Map<String, String> values = validProperties();
        values.put("jdk.extra.modules", "jdk.charsets,candles");
        assertFalse(InstallerConfig.parse(values).valid());
    }

    @Test
    void loadRejectsAMissingFile() {
        Path missing = Paths.get("target", "no-such-installer.properties");
        assertFalse(java.nio.file.Files.isRegularFile(missing));
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> InstallerConfig.load(missing)).getMessage().contains("not found"));
    }
}
