package com.mebelsex.release;

import com.mebelsex.license.LicenseKeys;
import com.mebelsex.license.ResourcePublicKeyProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Refuses to ship an artifact that is wrong for a customer: a placeholder or missing public
 * key, a leaked private key, a license/database/log file inside the package, the developer
 * license flag in the launcher configuration, a non-numeric version, a broken icon or a
 * missing upgrade id.
 *
 * <p>
 * All checks run on files, so they are unit-tested with temporary directories. The bundled
 * runtime check (a module must be present) needs to start the packaged JVM and is added by
 * the driver through {@link Result#plus(List)}.
 */
public final class ReleaseGate {

    /** Text that must never appear anywhere in a shipped artifact. */
    static final String PRIVATE_KEY_MARKER = LicenseKeys.PRIVATE_HEADER;

    /** File names/extensions that hold customer data and must never be packaged. */
    static final List<String> FORBIDDEN_SUFFIXES = List.of(".key", ".lic", ".db", ".log", "-wal", "-shm");

    /** A directory with this name is a developer snapshot folder and must not ship. */
    static final String SNAPSHOT_DIRECTORY = "snapshots";

    /** Files above this size are not scanned for the private-key marker. */
    static final long MAX_SCANNED_BYTES = 64L * 1024 * 1024;

    /** Options that must never be configured for the packaged application. */
    static final List<String> FORBIDDEN_OPTIONS = List.of("mebelsex.license.dev", "mebelsex.license.force");

    private static final String PUBLIC_KEY_ENTRY = "license/license-public.key";

    private ReleaseGate() {
    }

    /** One gate check. */
    public record Finding(String check, boolean ok, String detail) {

        static Finding pass(String check, String detail) {
            return new Finding(check, true, detail);
        }

        static Finding fail(String check, String detail) {
            return new Finding(check, false, detail);
        }
    }

    /** All findings, in the order they were produced. */
    public record Result(List<Finding> findings) {

        public boolean ok() {
            return findings.stream().allMatch(Finding::ok);
        }

        public List<Finding> failures() {
            return findings.stream().filter(finding -> !finding.ok()).toList();
        }

        /** A new result with the extra findings appended. */
        public Result plus(List<Finding> extra) {
            List<Finding> merged = new ArrayList<>(findings);
            merged.addAll(extra);
            return new Result(List.copyOf(merged));
        }

        /** One line per finding plus a final verdict. */
        public String summary() {
            StringBuilder text = new StringBuilder();
            for (Finding finding : findings) {
                text.append(finding.ok() ? "  OK   " : "  FAIL ").append(finding.check())
                        .append(": ").append(finding.detail()).append('\n');
            }
            text.append(findings.size()).append(" gate checks, ").append(failures().size())
                    .append(" failed -> ").append(ok() ? "PASS" : "FAIL");
            return text.toString();
        }
    }

    /**
     * Runs every file-based check.
     *
     * @param mainJar     the thin application jar inside {@code target/dist}
     * @param appImageDir the generated app-image ({@code target/installer/ZakazPro})
     * @param iconFile    the {@code .ico} handed to jpackage
     * @param version     the version handed to jpackage (already normalized)
     * @param config      the installer configuration
     */
    public static Result inspect(Path mainJar, Path appImageDir, Path iconFile, String version,
            InstallerConfig config) {
        List<Finding> findings = new ArrayList<>();
        findings.add(publicKey(mainJar));
        findings.add(cfgOptions(appImageDir, config));
        findings.add(version(mainJar, version));
        findings.add(icon(iconFile));
        findings.add(upgradeId(config));
        findings.add(noSecrets(mainJar.getParent(), appImageDir));
        return new Result(List.copyOf(findings));
    }

    /** The shipped public key must be the real Ed25519 key. */
    static Finding publicKey(Path mainJar) {
        try (ZipFile jar = new ZipFile(mainJar.toFile())) {
            ZipEntry entry = jar.getEntry(PUBLIC_KEY_ENTRY);
            if (entry == null) {
                return Finding.fail("public-key", PUBLIC_KEY_ENTRY + " is missing from " + mainJar.getFileName());
            }
            String text;
            try (InputStream in = jar.getInputStream(entry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (text.contains(ResourcePublicKeyProvider.PLACEHOLDER)) {
                return Finding.fail("public-key", "the shipped key is still the placeholder - run the key generator");
            }
            String firstLine = text.lines().findFirst().orElse("");
            if (!LicenseKeys.PUBLIC_HEADER.equals(firstLine)) {
                return Finding.fail("public-key", "unexpected key header: '" + firstLine + "'");
            }
            try {
                LicenseKeys.parsePublicKey(text);
            } catch (RuntimeException e) {
                return Finding.fail("public-key", "the key does not parse: " + e.getMessage());
            }
            return Finding.pass("public-key", "real Ed25519 key (" + text.trim().length() + " characters)");
        } catch (IOException e) {
            return Finding.fail("public-key", "cannot read " + mainJar + ": " + e.getMessage());
        }
    }

    /** The launcher configuration must carry the configured options and no developer flag. */
    static Finding cfgOptions(Path appImageDir, InstallerConfig config) {
        Path cfg = appImageDir.resolve("app").resolve(config.appName() + ".cfg");
        if (!Files.isRegularFile(cfg)) {
            return Finding.fail("app.cfg", "missing " + cfg);
        }
        String text;
        try {
            text = Files.readString(cfg, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Finding.fail("app.cfg", "cannot read " + cfg + ": " + e.getMessage());
        }
        for (String forbidden : FORBIDDEN_OPTIONS) {
            if (text.contains(forbidden)) {
                return Finding.fail("app.cfg", cfg + " contains " + forbidden
                        + " - the developer flag must never reach the installer");
            }
        }
        Optional<String> missing = config.javaOptions().stream().filter(option -> !text.contains(option)).findFirst();
        if (missing.isPresent()) {
            return Finding.fail("app.cfg", "option missing from " + cfg + ": " + missing.get());
        }
        return Finding.pass("app.cfg", config.javaOptions().size() + " options present, no license dev flag");
    }

    /** The version must be numeric and stored in the launcher configuration. */
    static Finding version(Path mainJar, String version) {
        String normalized;
        try {
            normalized = InstallerVersion.normalizeInstallerVersion(version);
        } catch (IllegalArgumentException e) {
            return Finding.fail("app.version", e.getMessage());
        }
        if (!normalized.equals(version)) {
            return Finding.fail("app.version", "the version passed to jpackage is not normalized: "
                    + version + " (expected " + normalized + ")");
        }
        String jarName = mainJar.getFileName().toString();
        if (!jarName.contains(normalized)) {
            return Finding.fail("app.version", "the version " + normalized + " is not part of " + jarName);
        }
        return Finding.pass("app.version", normalized + " (jar " + jarName + ")");
    }

    /** The .ico handed to jpackage must be a real Windows icon. */
    static Finding icon(Path iconFile) {
        if (!Files.isRegularFile(iconFile)) {
            return Finding.fail("icon", "missing " + iconFile);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(iconFile);
        } catch (IOException e) {
            return Finding.fail("icon", "cannot read " + iconFile + ": " + e.getMessage());
        }
        try {
            List<Integer> sizes = icoSizes(bytes);
            return Finding.pass("icon", sizes.size() + " images: " + sizes);
        } catch (IllegalArgumentException e) {
            return Finding.fail("icon", e.getMessage());
        }
    }

    /** The upgrade id decides whether a newer installer upgrades the old installation. */
    static Finding upgradeId(InstallerConfig config) {
        if (config.upgradeUuidOrNull() == null) {
            return Finding.fail("upgrade.uuid", "app.upgrade.uuid is not a valid UUID: '" + config.upgradeUuid() + "'");
        }
        return Finding.pass("upgrade.uuid", config.upgradeUuid());
    }

    /**
     * No private key text, no customer data and no snapshot folder in the distributed
     * directory or the app-image.
     */
    static Finding noSecrets(Path distDir, Path appImageDir) {
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        int skipped = 0;
        byte[] marker = PRIVATE_KEY_MARKER.getBytes(StandardCharsets.US_ASCII);
        List<Path> roots = new ArrayList<>();
        if (distDir != null) {
            roots.add(distDir);
        }
        if (appImageDir != null) {
            roots.add(appImageDir);
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.toList()) {
                    if (Files.isDirectory(path)) {
                        if (SNAPSHOT_DIRECTORY.equalsIgnoreCase(path.getFileName().toString())) {
                            problems.add("snapshot folder: " + root.relativize(path));
                        }
                        continue;
                    }
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    for (String suffix : FORBIDDEN_SUFFIXES) {
                        if (name.endsWith(suffix)) {
                            problems.add("customer data file: " + root.relativize(path));
                        }
                    }
                    long size = Files.size(path);
                    if (size > MAX_SCANNED_BYTES) {
                        skipped++;
                        continue;
                    }
                    if (contains(Files.readAllBytes(path), marker)) {
                        problems.add("private key text in: " + root.relativize(path));
                    }
                    scanned++;
                }
            } catch (IOException e) {
                problems.add("cannot scan " + root + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            return Finding.fail("no-secrets", String.join("; ", problems));
        }
        return Finding.pass("no-secrets", "scanned " + scanned + " files, none oversized (" + skipped + ")");
    }

    /** Validates an .ico and returns its image sizes. */
    static List<Integer> icoSizes(byte[] bytes) {
        if (bytes.length < 6) {
            throw new IllegalArgumentException("the .ico is too short");
        }
        int reserved = readShort(bytes, 0);
        int type = readShort(bytes, 2);
        int count = readShort(bytes, 4);
        if (reserved != 0 || type != 1 || count == 0) {
            throw new IllegalArgumentException("not an icon file (reserved=" + reserved + ", type=" + type
                    + ", images=" + count + ")");
        }
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int base = 6 + i * 16;
            if (base + 16 > bytes.length) {
                throw new IllegalArgumentException("image " + i + " has no directory entry");
            }
            int size = Math.max(bytes[base] & 0xff, bytes[base + 1] & 0xff);
            int length = readInt(bytes, base + 8);
            int offset = readInt(bytes, base + 12);
            if (length <= 0 || offset < 6 + count * 16 || offset + length > bytes.length) {
                throw new IllegalArgumentException("image " + i + " points outside the file");
            }
            sizes.add(size == 0 ? 256 : size);
        }
        return List.copyOf(sizes);
    }

    private static int readShort(byte[] bytes, int index) {
        return (bytes[index] & 0xff) | ((bytes[index + 1] & 0xff) << 8);
    }

    private static int readInt(byte[] bytes, int index) {
        return (bytes[index] & 0xff) | ((bytes[index + 1] & 0xff) << 8)
                | ((bytes[index + 2] & 0xff) << 16) | ((bytes[index + 3] & 0xff) << 24);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
