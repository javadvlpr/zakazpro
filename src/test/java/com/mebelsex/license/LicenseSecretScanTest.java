package com.mebelsex.license;

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
 * Fails if any file in the project tree contains the private key file header.
 * Build output ({@code target/}) and tooling/VCS metadata (any dot-directory,
 * including {@code .git/}) are skipped. Only the two sources that must mention
 * the header to implement and test the format are allowed to contain it.
 */
class LicenseSecretScanTest {

    private static final String PRIVATE_HEADER = "MEBELSEX-PRIVATE-KEY-1";

    private static final Set<String> ALLOWED_FILE_NAMES = Set.of(
            "LicenseKeys.java", "LicenseSecretScanTest.java");

    @Test
    void noPrivateKeyMaterialInTheProjectTree() throws IOException {
        Path root = Paths.get("").toAbsolutePath().normalize();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (isSkipped(root, file) || ALLOWED_FILE_NAMES.contains(file.getFileName().toString())) {
                    continue;
                }
                if (containsPrivateHeader(file)) {
                    offenders.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "private key material must never be committed: " + offenders);
    }

    private static boolean isSkipped(Path root, Path file) {
        Path relative = root.relativize(file);
        String first = relative.getName(0).toString();
        return first.equals("target") || first.startsWith(".");
    }

    private static boolean containsPrivateHeader(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (byte b : bytes) {
                if (b == 0) {
                    return false; // binary file
                }
            }
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(PRIVATE_HEADER);
        } catch (IOException e) {
            return false;
        }
    }
}
