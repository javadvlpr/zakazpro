package com.mebelsex.license.tool;

import com.mebelsex.license.LicenseKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of {@link LicenseCli} with temp dirs and in-memory streams. */
class LicenseCliTest {

    private static final String MACHINE = "MX-ABCD-EFGH-JKLM-NPQR";

    @TempDir
    Path temp;

    @Test
    void keygenCreatesParseableKeyFiles() throws Exception {
        Path dir = temp.resolve("keys");
        Result result = run("keygen", "--out-dir", dir.toString());

        assertEquals(0, result.exit, result.err);
        Path privateFile = dir.resolve("license-private.key");
        Path publicFile = dir.resolve("license-public.key");
        assertTrue(Files.exists(privateFile) && Files.exists(publicFile));
        assertTrue(result.out.contains("Private kalitni hech kimga bermang"));

        PrivateKey privateKey = LicenseKeys.parsePrivateKey(Files.readString(privateFile, StandardCharsets.UTF_8));
        assertNotNull(privateKey);
        assertNotNull(LicenseKeys.parsePublicKey(Files.readString(publicFile, StandardCharsets.UTF_8)));
    }

    @Test
    void keygenRefusesADirectoryInsideTheProjectTree() {
        Path insideProject = Path.of("target").resolve("license-cli-refuse-" + UUID.randomUUID());
        Result result = run("keygen", "--out-dir", insideProject.toString());

        assertEquals(1, result.exit);
        assertTrue(result.err.contains("loyiha"), result.err);
        assertFalse(Files.exists(insideProject));
    }

    @Test
    void keygenRefusesToOverwriteExistingKeys() {
        Path dir = temp.resolve("keys");
        assertEquals(0, run("keygen", "--out-dir", dir.toString()).exit);

        Result second = run("keygen", "--out-dir", dir.toString());
        assertEquals(1, second.exit);
        assertTrue(second.err.contains("bor"), second.err);
    }

    @Test
    void keygenOutputNeverContainsPrivateKeyMaterial() throws Exception {
        Path dir = temp.resolve("keys");
        Result result = run("keygen", "--out-dir", dir.toString());

        String privateText = Files.readString(dir.resolve("license-private.key"), StandardCharsets.UTF_8);
        String base64Body = privateText.trim().split("\n")[1];
        assertFalse(result.out.contains(base64Body), "private key bytes leaked to stdout");
        assertFalse(result.err.contains(base64Body), "private key bytes leaked to stderr");
    }

    @Test
    void issueThenInspectSaysValid() throws Exception {
        Path dir = temp.resolve("keys");
        assertEquals(0, run("keygen", "--out-dir", dir.toString()).exit);

        Path license = temp.resolve("mijoz.lic");
        Result issue = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mebel Sex MChJ",
                "--machine", MACHINE,
                "--out", license.toString());
        assertEquals(0, issue.exit, issue.err);
        assertTrue(Files.isRegularFile(license));
        assertTrue(issue.out.contains("muddatsiz"), issue.out);

        Result inspect = run("inspect",
                "--license", license.toString(),
                "--public-key", dir.resolve("license-public.key").toString(),
                "--machine", MACHINE);
        assertEquals(0, inspect.exit, inspect.err);
        assertTrue(inspect.out.contains("to'g'ri"), inspect.out);
        assertTrue(inspect.out.contains(MACHINE), inspect.out);
    }

    @Test
    void issueWithFutureExpiryPrintsTheDate() throws Exception {
        Path dir = temp.resolve("keys");
        assertEquals(0, run("keygen", "--out-dir", dir.toString()).exit);
        Path license = temp.resolve("muddatli.lic");
        Result issue = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--expires", "2030-12-31",
                "--out", license.toString());
        assertEquals(0, issue.exit, issue.err);
        assertTrue(issue.out.contains("2030-12-31"), issue.out);
    }

    @Test
    void issueRejectsPastExpiry() throws Exception {
        Path dir = keygenDir();
        Result issue = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--expires", "2020-01-01",
                "--out", temp.resolve("eski.lic").toString());
        assertEquals(1, issue.exit);
        assertTrue(issue.err.contains("Muddati"), issue.err);
    }

    @Test
    void issueRejectsAnInvalidMachineCode() throws Exception {
        Path dir = keygenDir();
        Result issue = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", "salom-dunyo",
                "--out", temp.resolve("x.lic").toString());
        assertEquals(1, issue.exit);
        assertTrue(issue.err.contains("Machine ID"), issue.err);
    }

    @Test
    void issueRejectsABlankCustomer() throws Exception {
        Path dir = keygenDir();
        Result issue = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "   ",
                "--machine", MACHINE,
                "--out", temp.resolve("x.lic").toString());
        assertEquals(1, issue.exit);
    }

    @Test
    void issueRefusesToOverwriteWithoutForce() throws Exception {
        Path dir = keygenDir();
        Path license = temp.resolve("bir.lic");
        String[] args = {"issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--out", license.toString()};
        assertEquals(0, run(args).exit);

        Result second = run(args);
        assertEquals(1, second.exit);
        assertTrue(second.err.contains("--force"), second.err);

        Result forced = run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--out", license.toString(),
                "--force");
        assertEquals(0, forced.exit, forced.err);
    }

    @Test
    void inspectWithAnotherPublicKeyFailsVerification() throws Exception {
        Path keys = keygenDir();
        Path otherKeys = temp.resolve("boshqa-keys");
        assertEquals(0, run("keygen", "--out-dir", otherKeys.toString()).exit);
        Path license = temp.resolve("mijoz.lic");
        assertEquals(0, run("issue",
                "--private-key", keys.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--out", license.toString()).exit);

        Result inspect = run("inspect",
                "--license", license.toString(),
                "--public-key", otherKeys.resolve("license-public.key").toString(),
                "--machine", MACHINE);
        assertEquals(2, inspect.exit);
        assertTrue(inspect.out.contains("imzo"), inspect.out);
    }

    @Test
    void inspectReportsAWrongMachine() throws Exception {
        Path dir = keygenDir();
        Path license = temp.resolve("mijoz.lic");
        assertEquals(0, run("issue",
                "--private-key", dir.resolve("license-private.key").toString(),
                "--customer", "Mijoz",
                "--machine", MACHINE,
                "--out", license.toString()).exit);

        Result inspect = run("inspect",
                "--license", license.toString(),
                "--public-key", dir.resolve("license-public.key").toString(),
                "--machine", "MX-2UJ9-8Q3J-Z7EK-5R7F");
        assertEquals(2, inspect.exit);
        assertTrue(inspect.out.contains("boshqa kompyuter"), inspect.out);
    }

    @Test
    void unknownCommandIsAUsageError() {
        Result result = run("salom");
        assertEquals(1, result.exit);
        assertTrue(result.err.contains("Noma'lum buyruq"));
    }

    @Test
    void missingArgumentsAreAUsageError() {
        assertEquals(1, run("issue").exit);
        assertEquals(1, run("inspect").exit);
        assertEquals(1, run("keygen").exit);
    }

    private Path keygenDir() {
        Path dir = temp.resolve("keys-" + UUID.randomUUID());
        assertEquals(0, run("keygen", "--out-dir", dir.toString()).exit);
        return dir;
    }

    private static Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            exit = LicenseCli.run(args, out, err);
        }
        return new Result(exit,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    private static final class Result {
        final int exit;
        final String out;
        final String err;

        Result(int exit, String out, String err) {
            this.exit = exit;
            this.out = out;
            this.err = err;
        }
    }
}
