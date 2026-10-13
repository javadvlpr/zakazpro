package com.mebelsex.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the release gate (temporary directories only). */
class ReleaseGateTest {

    private static final String VERSION = "1.0.0";
    private static final String UPGRADE_UUID = "c3138c4d-c7aa-4b27-a524-eb2a1c59d38e";
    private static final String PUBLIC_KEY_ENTRY = "license/license-public.key";

    @TempDir
    Path temp;

    @Test
    void acceptsACorrectPackage() throws Exception {
        Fixture fixture = fixture();

        ReleaseGate.Result result = ReleaseGate.inspect(fixture.jar, fixture.appImage, fixture.icon, VERSION,
                fixture.config);

        assertTrue(result.ok(), result::summary);
        assertTrue(result.summary().contains("PASS"), result.summary());
        List<String> checks = result.findings().stream().map(ReleaseGate.Finding::check).toList();
        assertTrue(checks.containsAll(List.of("public-key", "app.cfg", "app.version", "icon", "upgrade.uuid",
                "no-secrets")), checks.toString());
    }

    @Test
    void rejectsThePlaceholderPublicKey() throws Exception {
        Map<String, String> entries = new HashMap<>();
        entries.put(PUBLIC_KEY_ENTRY, "PLACEHOLDER-RUN-KEYGEN\n");
        Path jar = writeJar(temp.resolve("placeholder.jar"), entries);

        ReleaseGate.Finding finding = ReleaseGate.publicKey(jar);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("placeholder"), finding.detail());
    }

    @Test
    void rejectsAMissingPublicKey() throws Exception {
        Path jar = writeJar(temp.resolve("empty.jar"), Map.of("README.txt", "nothing here"));

        ReleaseGate.Finding finding = ReleaseGate.publicKey(jar);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains(PUBLIC_KEY_ENTRY), finding.detail());
    }

    @Test
    void rejectsAKeyThatDoesNotParse() throws Exception {
        Map<String, String> entries = new HashMap<>();
        entries.put(PUBLIC_KEY_ENTRY, "MEBELSEX-PUBLIC-KEY-1\nnot-base64-der\n");
        Path jar = writeJar(temp.resolve("broken.jar"), entries);

        assertFalse(ReleaseGate.publicKey(jar).ok());
    }

    @Test
    void acceptsTheRealPublicKey() throws Exception {
        Map<String, String> entries = new HashMap<>();
        entries.put(PUBLIC_KEY_ENTRY, realPublicKey());
        Path jar = writeJar(temp.resolve("real.jar"), entries);

        ReleaseGate.Finding finding = ReleaseGate.publicKey(jar);

        assertTrue(finding.ok(), finding.detail());
        assertTrue(finding.detail().contains("Ed25519"), finding.detail());
    }

    @Test
    void rejectsTheDeveloperFlagInTheLauncherConfig() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.cfg, Files.readString(fixture.cfg) + "java-options=-Dmebelsex.license.dev=true\n");

        ReleaseGate.Finding finding = ReleaseGate.cfgOptions(fixture.appImage, fixture.config);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("mebelsex.license"), finding.detail());
    }

    @Test
    void rejectsAMissingConfiguredOption() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.cfg, """
                [Application]
                app.mainclass=com.mebelsex.Launcher

                [JavaOptions]
                java-options=-Djpackage.app-version=1.0.0
                """);

        ReleaseGate.Finding finding = ReleaseGate.cfgOptions(fixture.appImage, fixture.config);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("-Dfile.encoding=UTF-8"), finding.detail());
    }

    @Test
    void rejectsANonNumericOrMismatchedVersion() {
        assertFalse(ReleaseGate.version(Path.of("mebelsex-1.0.0.jar"), "1.0.0-SNAPSHOT").ok());
        assertFalse(ReleaseGate.version(Path.of("mebelsex-1.0.0.jar"), "abc").ok());
        assertFalse(ReleaseGate.version(Path.of("mebelsex-1.0.0.jar"), "2.0.0").ok());
        assertTrue(ReleaseGate.version(Path.of("mebelsex-1.0.0.jar"), "1.0.0").ok());
    }

    @Test
    void rejectsAMissingOrBrokenIcon() throws Exception {
        assertFalse(ReleaseGate.icon(temp.resolve("none.ico")).ok());

        Path broken = temp.resolve("broken.ico");
        Files.write(broken, new byte[] { 0, 0, 1, 0, 2, 0 });
        ReleaseGate.Finding finding = ReleaseGate.icon(broken);
        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("outside the file") || finding.detail().contains("no directory entry"),
                finding.detail());

        Fixture fixture = fixture();
        ReleaseGate.Finding good = ReleaseGate.icon(fixture.icon);
        assertTrue(good.ok(), good.detail());
        assertTrue(good.detail().contains("256"), good.detail());
    }

    @Test
    void rejectsABadUpgradeUuid() {
        assertFalse(ReleaseGate.upgradeId(config(Map.of("app.upgrade.uuid", "00000000-0000-0000-0000-000000000000"))).ok());
        assertFalse(ReleaseGate.upgradeId(config(Map.of("app.upgrade.uuid", "nope"))).ok());
        assertTrue(ReleaseGate.upgradeId(config(Map.of("app.upgrade.uuid", UPGRADE_UUID))).ok());
    }

    @Test
    void findsAPrivateKeyMarkerAnywhereInThePackage() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.distLib.resolve("leaked.txt"),
                "-----BEGIN " + ReleaseGate.PRIVATE_KEY_MARKER + "-----");

        ReleaseGate.Finding finding = ReleaseGate.noSecrets(fixture.distLib.getParent(), fixture.appImage);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("private key text in: lib"), finding.detail());
        assertTrue(finding.detail().contains("leaked.txt"), finding.detail());
    }

    @Test
    void rejectsCustomerDataAndSnapshotFolders() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.distLib.resolve("customer.db"), "x");
        Files.writeString(fixture.appImage.resolve("app").resolve("old.log"), "x");
        Files.createDirectories(fixture.appImage.resolve("snapshots"));

        ReleaseGate.Finding finding = ReleaseGate.noSecrets(fixture.distLib.getParent(), fixture.appImage);

        assertFalse(finding.ok());
        assertTrue(finding.detail().contains("customer.db"), finding.detail());
        assertTrue(finding.detail().contains("old.log"), finding.detail());
        assertTrue(finding.detail().contains("snapshot folder"), finding.detail());
    }

    @Test
    void summaryListsEveryFailure() {
        ReleaseGate.Result result = new ReleaseGate.Result(List.of(
                new ReleaseGate.Finding("icon", true, "7 images"),
                new ReleaseGate.Finding("public-key", false, "placeholder")))
                .plus(List.of(new ReleaseGate.Finding("runtime.modules", false, "jdk.localedata missing")));

        assertFalse(result.ok());
        assertEquals(List.of("public-key", "runtime.modules"),
                result.failures().stream().map(ReleaseGate.Finding::check).toList());
        assertTrue(result.summary().contains("FAIL public-key: placeholder"), result.summary());
        assertTrue(result.summary().contains("3 gate checks, 2 failed -> FAIL"), result.summary());
    }

    // ------------------------------------------------------------------

    private record Fixture(Path jar, Path distLib, Path appImage, Path cfg, Path icon, InstallerConfig config) {
    }

    private Fixture fixture() throws Exception {
        Path dist = temp.resolve("dist");
        Path distLib = dist.resolve("lib");
        Files.createDirectories(distLib);
        Files.writeString(distLib.resolve("sqlite-jdbc.jar"), "not really a jar");

        Map<String, String> entries = new HashMap<>();
        entries.put(PUBLIC_KEY_ENTRY, realPublicKey());
        Path jar = writeJar(dist.resolve("mebelsex-" + VERSION + ".jar"), entries);

        Path appImage = temp.resolve("installer").resolve("ZakazPro");
        Path appDir = appImage.resolve("app");
        Files.createDirectories(appDir);
        Files.createDirectories(appImage.resolve("runtime").resolve("bin"));
        InstallerConfig config = config(Map.of());
        Path cfg = appDir.resolve("ZakazPro.cfg");
        StringBuilder cfgText = new StringBuilder("[Application]\napp.mainclass=com.mebelsex.Launcher\n\n[JavaOptions]\n"
                + "java-options=-Djpackage.app-version=" + VERSION + "\n");
        for (String option : config.javaOptions()) {
            cfgText.append("java-options=").append(option).append('\n');
        }
        Files.writeString(cfg, cfgText.toString());

        Path icon = temp.resolve("zakazpro.ico");
        IconBuilder.build(writeSourcePng(), icon, temp.resolve("icons"));

        return new Fixture(jar, distLib, appImage, cfg, icon, config);
    }

    private Path writeSourcePng() throws Exception {
        Path png = temp.resolve("source.png");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(300, 300,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(image, "png", png.toFile());
        return png;
    }

    private static InstallerConfig config(Map<String, String> overrides) {
        Map<String, String> values = new HashMap<>();
        values.put("app.name", "ZakazPro");
        values.put("app.vendor", "ZakazPro");
        values.put("app.description", "Tavsif");
        values.put("app.copyright", "(c) 2026 ZakazPro");
        values.put("app.menu.group", "ZakazPro");
        values.put("app.upgrade.uuid", UPGRADE_UUID);
        values.put("app.jvm.options", "-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xmx768m -XX:+UseSerialGC");
        values.put("jdk.extra.modules", "");
        values.put("icon.path", "installer/assets/zakazpro.ico");
        values.put("icon.source", "installer/assets/zakazpro-icon.png");
        values.putAll(overrides);
        return InstallerConfig.parse(values);
    }

    private static String realPublicKey() throws Exception {
        try (InputStream in = ReleaseGateTest.class.getResourceAsStream("/license/license-public.key")) {
            assertTrue(in != null, "the real public key must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path writeJar(Path jar, Map<String, String> entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return jar;
    }
}
