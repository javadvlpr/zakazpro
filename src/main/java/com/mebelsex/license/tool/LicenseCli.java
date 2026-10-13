package com.mebelsex.license.tool;

import com.mebelsex.license.LicenseCheck;
import com.mebelsex.license.LicenseFormat;
import com.mebelsex.license.LicenseKeys;
import com.mebelsex.license.LicensePayload;
import com.mebelsex.license.LicenseVerifier;
import com.mebelsex.license.MachineId;
import com.mebelsex.license.MachineIdException;
import com.mebelsex.license.WindowsMachineGuidSource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-only command line tool for the offline license system. Runs on the JDK
 * only: no Spring, no JavaFX.
 *
 * <p>Commands: {@code machine}, {@code keygen}, {@code issue}, {@code inspect}.
 * Exit codes: 0 ok, 1 usage/validation error, 2 verification failed.
 *
 * <p>The visible text is intentionally Uzbek and lives here (not in the app
 * message bundle): this tool is used by the workshop owner, not the app UI.
 */
public final class LicenseCli {

    private static final String PRIVATE_FILE = "license-private.key";
    private static final String PUBLIC_FILE = "license-public.key";

    private LicenseCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs a command. Never throws for expected problems.
     *
     * @return the process exit code
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            usage(err);
            return 1;
        }
        try {
            switch (args[0]) {
                case "machine":
                    return machine(out);
                case "keygen":
                    return keygen(args, out);
                case "issue":
                    return issue(args, out);
                case "inspect":
                    return inspect(args, out);
                default:
                    err.println("Noma'lum buyruq: " + args[0]);
                    usage(err);
                    return 1;
            }
        } catch (CliError e) {
            err.println(e.getMessage());
            return e.exitCode;
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            err.println("Kutilmagan xatolik: " + message);
            return 2;
        }
    }

    private static int machine(PrintStream out) throws CliError {
        try {
            String guid = new WindowsMachineGuidSource().readRawId();
            out.println("Machine ID: " + MachineId.fromRawId(guid));
            return 0;
        } catch (MachineIdException e) {
            throw new CliError(1, "Machine ID o'qilmadi: " + e.getMessage());
        }
    }

    private static int keygen(String[] args, PrintStream out) throws CliError {
        Map<String, String> options = parseOptions(args, 1);
        Path directory = Path.of(require(options, "out-dir")).toAbsolutePath().normalize();
        ensureOutsideProjectTree(directory);

        Path privateFile = directory.resolve(PRIVATE_FILE);
        Path publicFile = directory.resolve(PUBLIC_FILE);
        if (Files.exists(privateFile) || Files.exists(publicFile)) {
            throw new CliError(1, "Bu papkada kalitlar bor, ustidan yozilmaydi: " + directory);
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new CliError(1, "Papka yaratilmadi: " + directory);
        }

        KeyPair keys = LicenseKeys.generate();
        write(privateFile, LicenseKeys.encodePrivateKeyText(keys.getPrivate()));
        write(publicFile, LicenseKeys.encodePublicKeyText(keys.getPublic()));

        out.println("Kalitlar yaratildi: " + directory);
        out.println("  " + privateFile.getFileName() + "  (private kalit)");
        out.println("  " + publicFile.getFileName() + "  (public kalit)");
        out.println("Private kalitni hech kimga bermang va nusxasini ikki xavfsiz joyda saqlang.");
        out.println("Public kalitni src\\main\\resources\\license\\license-public.key ga nusxalang.");
        return 0;
    }

    private static int issue(String[] args, PrintStream out) throws CliError {
        Map<String, String> options = parseOptions(args, 1);
        Path privateKeyFile = path(options, "private-key");
        String customer = require(options, "customer");
        String machineRaw = require(options, "machine");
        String expiresRaw = options.get("expires");
        Path outputFile = path(options, "out");
        boolean force = options.containsKey("force");

        if (customer.isBlank()) {
            throw new CliError(1, "Mijoz nomi bo'sh bo'lmasin.");
        }
        String machine;
        try {
            machine = MachineId.normalize(machineRaw);
        } catch (IllegalArgumentException e) {
            throw new CliError(1, "Machine ID noto'g'ri: " + machineRaw);
        }

        LocalDate today = LocalDate.now();
        LocalDate expires = parseExpires(expiresRaw, today);

        PrivateKey privateKey = readPrivateKey(privateKeyFile);
        if (Files.exists(outputFile) && !force) {
            throw new CliError(1, "Fayl allaqachon mavjud (--force bilan almashtiring): " + outputFile);
        }

        LicensePayload payload = new LicensePayload(LicenseFormat.FORMAT_VERSION,
                UUID.randomUUID().toString(), customer.trim(), machine, today, expires, List.of());
        String text = LicenseFormat.encode(payload, privateKey);

        PublicKey publicKey = resolvePublicKey(options, privateKeyFile);
        LicenseCheck check = LicenseVerifier.verify(text, Optional.of(publicKey), machine, today);
        if (check.result() != LicenseCheck.Result.VALID) {
            throw new CliError(2, "Yaratilgan litsenziya tekshiruvdan o'tmadi: " + check.result());
        }

        write(outputFile, text);
        out.println("Litsenziya yaratildi: " + outputFile);
        out.println("  Mijoz: " + payload.customer());
        out.println("  Machine ID: " + payload.machineId());
        out.println("  Berilgan: " + payload.issuedAt());
        out.println("  Muddati: " + (expires == null ? "muddatsiz" : expires.toString()));
        out.println("  Litsenziya ID: " + payload.licenseId());
        return 0;
    }

    private static int inspect(String[] args, PrintStream out) throws CliError {
        Map<String, String> options = parseOptions(args, 1);
        Path licenseFile = path(options, "license");
        Path publicKeyFile = path(options, "public-key");
        String machineOption = options.get("machine");

        String text = readText(licenseFile, "Litsenziya fayli o'qilmadi: ");
        PublicKey publicKey = readPublicKey(publicKeyFile);
        String machine = (machineOption == null || machineOption.isBlank())
                ? currentMachineOrNull() : machineOption;

        LicenseCheck check = LicenseVerifier.verify(text, Optional.of(publicKey), machine, LocalDate.now());

        out.println("Litsenziya: " + licenseFile);
        if (check.payload() != null) {
            LicensePayload payload = check.payload();
            out.println("  Mijoz: " + payload.customer());
            out.println("  Machine ID: " + payload.machineId());
            out.println("  Berilgan: " + payload.issuedAt());
            out.println("  Muddati: " + (payload.expiresAt() == null ? "muddatsiz" : payload.expiresAt()));
            out.println("  Litsenziya ID: " + payload.licenseId());
        }
        if (check.daysLeft() != null) {
            out.println("  Qolgan kun: " + check.daysLeft());
        }
        out.println("  Holat: " + describe(check.result()));

        return (check.result() == LicenseCheck.Result.VALID || check.result() == LicenseCheck.Result.EXPIRED) ? 0 : 2;
    }

    private static String describe(LicenseCheck.Result result) {
        switch (result) {
            case VALID:
                return "litsenziya to'g'ri";
            case EXPIRED:
                return "litsenziya muddati tugagan";
            case WRONG_MACHINE:
                return "litsenziya boshqa kompyuter uchun";
            case BAD_SIGNATURE:
                return "imzo noto'g'ri";
            case MALFORMED:
                return "fayl formati noto'g'ri";
            case UNSUPPORTED_VERSION:
                return "litsenziya versiyasi qo'llab-quvvatlanmaydi";
            case NO_PUBLIC_KEY:
                return "public kalit yo'q";
            case NO_MACHINE_ID:
                return "machine ID aniqlanmadi";
            default:
                return result.name();
        }
    }

    private static LocalDate parseExpires(String raw, LocalDate today) throws CliError {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LocalDate expires;
        try {
            expires = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new CliError(1, "Sana formati noto'g'ri (yyyy-MM-dd): " + raw);
        }
        if (expires.isBefore(today)) {
            throw new CliError(1, "Muddati o'tgan litsenziya yaratilmaydi: " + raw);
        }
        return expires;
    }

    private static PrivateKey readPrivateKey(Path file) throws CliError {
        if (!Files.isRegularFile(file)) {
            throw new CliError(1, "Private kalit topilmadi: " + file);
        }
        try {
            return LicenseKeys.parsePrivateKey(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            throw new CliError(1, "Private kalit o'qilmadi: " + file);
        }
    }

    private static PublicKey readPublicKey(Path file) throws CliError {
        if (!Files.isRegularFile(file)) {
            throw new CliError(1, "Public kalit topilmadi: " + file);
        }
        try {
            return LicenseKeys.parsePublicKey(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            throw new CliError(1, "Public kalit o'qilmadi: " + file);
        }
    }

    private static PublicKey resolvePublicKey(Map<String, String> options, Path privateKeyFile) throws CliError {
        Path publicFile = options.containsKey("public-key")
                ? Path.of(options.get("public-key"))
                : privateKeyFile.toAbsolutePath().getParent().resolve(PUBLIC_FILE);
        return readPublicKey(publicFile);
    }

    private static String readText(Path file, String errorPrefix) throws CliError {
        if (!Files.isRegularFile(file)) {
            throw new CliError(1, errorPrefix + file);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CliError(1, errorPrefix + file);
        }
    }

    private static String currentMachineOrNull() {
        try {
            return MachineId.fromRawId(new WindowsMachineGuidSource().readRawId());
        } catch (MachineIdException e) {
            return null;
        }
    }

    private static void write(Path file, String text) throws CliError {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new CliError(1, "Fayl yozilmadi: " + file);
        }
    }

    /** Refuses a key directory that lives in (or under) a Maven project tree. */
    private static void ensureOutsideProjectTree(Path directory) throws CliError {
        Set<Path> roots = new LinkedHashSet<>();
        Path fromCwd = findProjectRoot(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            roots.add(fromCwd);
        }
        Path fromDir = findProjectRoot(directory);
        if (fromDir != null) {
            roots.add(fromDir);
        }
        for (Path root : roots) {
            if (directory.startsWith(root)) {
                throw new CliError(1, "Kalitlarni loyiha papkasi ichida saqlamang: " + directory);
            }
        }
    }

    private static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static Map<String, String> parseOptions(String[] args, int from) throws CliError {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = from; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) {
                throw new CliError(1, "Kutilmagan argument: " + argument);
            }
            String key = argument.substring(2);
            if ("force".equals(key)) {
                options.put(key, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new CliError(1, "Qiymat yo'q: " + argument);
            }
            options.put(key, args[++i]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) throws CliError {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new CliError(1, "Majburiy parametr yo'q: --" + key);
        }
        return value;
    }

    private static Path path(Map<String, String> options, String key) throws CliError {
        return Path.of(require(options, key));
    }

    private static void usage(PrintStream err) {
        err.println(com.mebelsex.common.AppInfo.PRODUCT_NAME + " litsenziya generatori");
        err.println("Buyruqlar:");
        err.println("  machine                                   - bu kompyuterning Machine ID sini chiqaradi");
        err.println("  keygen --out-dir <papka>                  - yangi kalit juftini yaratadi");
        err.println("  issue --private-key <fayl> --customer \"<nom>\" --machine <MX-kod>"
                + " [--expires yyyy-MM-dd] --out <fayl> [--force]  - litsenziya beradi");
        err.println("  inspect --license <fayl> --public-key <fayl> [--machine <MX-kod>]  - tekshiradi");
    }

    /** Expected CLI failure with its process exit code. */
    private static final class CliError extends Exception {

        private final int exitCode;

        CliError(int exitCode, String message) {
            super(message);
            this.exitCode = exitCode;
        }
    }
}
