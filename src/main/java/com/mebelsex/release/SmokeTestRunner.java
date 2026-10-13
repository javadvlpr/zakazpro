package com.mebelsex.release;

import com.mebelsex.MebelSexApplication;
import com.mebelsex.common.Messages;
import com.mebelsex.license.LicenseService;
import com.mebelsex.license.LicenseStatus;
import com.mebelsex.license.MachineId;
import com.mebelsex.license.MachineIdSource;
import com.mebelsex.license.ResourcePublicKeyProvider;
import com.mebelsex.settings.SettingsService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless proof that a packaged installation works: it starts the normal Spring context
 * against a temporary database (no JavaFX window, no toolkit) and checks the parts a jlink
 * runtime can break - migrations, settings, the license engine, the machine code
 * (reg.exe), Uzbek text with {@code jdk.localedata}, the JavaFX and ikonli classes on the
 * classpath, the icon resource and the default encoding.
 *
 * <p>
 * Every check is isolated: one failure never hides the others. The outcome is written as
 * {@code key=value} lines into {@code smoke-result.txt} next to the database, ending with
 * {@code RESULT=OK} or {@code RESULT=FAIL <reason>}. Exit code 0 = OK, 1 = failure.
 */
public final class SmokeTestRunner {

    private static final Logger log = LoggerFactory.getLogger(SmokeTestRunner.class);

    /** Final report line value when everything passed. */
    public static final String OK = "OK";

    /** Disables the daily backup during the smoke test. */
    private static final String BACKUP_SCHEDULER_PROPERTY = "mebelsex.backup.scheduler.enabled";

    /** Machine codes use the Crockford-style alphabet without I, O, 0 and 1. */
    private static final String MACHINE_ID_PATTERN = "MX-[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}";

    private SmokeTestRunner() {
    }

    /** One isolated check. */
    public record Check(String name, boolean ok, String detail) {
    }

    /** The whole run: the ordered checks plus the facts that are only recorded. */
    public record Report(List<Check> checks, List<String> facts) {

        /** {@code OK}, or {@code FAIL <check>: <detail>} for the first failing check. */
        public String result() {
            for (Check check : checks) {
                if (!check.ok()) {
                    return "FAIL " + check.name() + ": " + check.detail();
                }
            }
            return OK;
        }

        public boolean ok() {
            return OK.equals(result());
        }

        public List<String> failedChecks() {
            List<String> failed = new ArrayList<>();
            for (Check check : checks) {
                if (!check.ok()) {
                    failed.add(check.name());
                }
            }
            return failed;
        }
    }

    /** A probe that returns its detail line or throws to fail its check. */
    private interface Probe {
        String run() throws Exception;
    }

    /**
     * Boots the normal Spring context headless against {@code dbPath}, runs the checks and
     * writes the result file. Returns the process exit code (0 = OK, 1 = failure).
     */
    public static int run(Path dbPath, Path resultFile) {
        Report report;
        ConfigurableApplicationContext context = null;
        try {
            // The smoke test must never plan a backup, not even into a temp folder.
            System.setProperty(BACKUP_SCHEDULER_PROPERTY, "false");
            context = new SpringApplicationBuilder(MebelSexApplication.class)
                    .headless(true)
                    .run();
            report = inspect(context, dbPath);
        } catch (Throwable failure) {
            log.error("Smoke test startup failed", failure);
            report = new Report(List.of(new Check("startup", false, describe(failure))), baseFacts(dbPath));
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (RuntimeException e) {
                    log.warn("Could not close the smoke-test context: {}", e.getMessage());
                }
            }
        }

        try {
            Files.writeString(resultFile, format(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Could not write {}", resultFile, e);
            return 1;
        }
        return report.ok() ? 0 : 1;
    }

    /** Runs every check against an already started context. */
    public static Report inspect(ConfigurableApplicationContext context, Path dbPath) {
        List<String> facts = baseFacts(dbPath);
        List<Check> checks = new ArrayList<>();

        checks.add(check("flyway", () -> {
            Flyway flyway = context.getBean(Flyway.class);
            MigrationInfo[] applied = flyway.info().applied();
            MigrationInfo[] pending = flyway.info().pending();
            if (applied.length == 0) {
                throw new IllegalStateException("no migration was applied");
            }
            if (pending.length != 0) {
                throw new IllegalStateException(pending.length + " migration(s) still pending");
            }
            flyway.validate();
            return applied.length + " migrations applied, none pending, schema validated";
        }));

        checks.add(check("settings", () -> {
            SettingsService settings = context.getBean(SettingsService.class);
            settings.saveTheme("DARK");
            String written = settings.loadTheme();
            settings.saveTheme("LIGHT");
            if (!"DARK".equals(written)) {
                throw new IllegalStateException("read back '" + written + "' instead of DARK");
            }
            return "settings write/read round trip OK";
        }));

        checks.add(check("license", () -> {
            LicenseService service = context.getBean(LicenseService.class);
            LicenseStatus status = service.evaluate();
            if (status == null || status.state() == null) {
                throw new IllegalStateException("the license engine returned no state");
            }
            String keyText = readResource(ResourcePublicKeyProvider.DEFAULT_RESOURCE);
            if (keyText.isBlank()) {
                throw new IllegalStateException("the license public key resource is empty");
            }
            boolean placeholder = keyText.contains(ResourcePublicKeyProvider.PLACEHOLDER);
            facts.add("license.state=" + status.state());
            facts.add("license.reason=" + (status.reasonKey() == null ? "" : status.reasonKey()));
            facts.add("public.key.placeholder=" + placeholder);
            facts.add("public.key.header=" + keyText.lines().findFirst().orElse(""));
            return "state=" + status.state() + ", placeholder key=" + placeholder;
        }));

        checks.add(check("machine-id", () -> {
            MachineIdSource source = context.getBean(MachineIdSource.class);
            String code = MachineId.compute(source);
            if (!code.matches(MACHINE_ID_PATTERN)) {
                throw new IllegalStateException("unexpected machine code: " + code);
            }
            facts.add("machine.id=" + code);
            return code;
        }));

        checks.add(check("uzbek-text", () -> {
            String menu = Messages.get("menu.settings");
            if (!"Sozlamalar".equals(menu)) {
                throw new IllegalStateException("menu.settings=" + menu);
            }
            assertMessage("action.delete", "O'chirish");
            assertMessage("action.add", "Qo'shish");
            assertMessage("format.som", "so'm");
            String month = Month.OCTOBER.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("uz"));
            if ("October".equalsIgnoreCase(month)) {
                throw new IllegalStateException("jdk.localedata is missing (uz month = " + month + ")");
            }
            facts.add("uz.month=" + month);
            return "menu.settings=" + menu + ", month=" + month;
        }));

        checks.add(check("javafx-classes", () -> {
            ClassLoader loader = SmokeTestRunner.class.getClassLoader();
            // Loaded, not initialized: Control's static initializer calls the FX toolkit,
            // which a headless smoke test deliberately never starts (that is normal and not
            // a packaging problem). Loading still proves the classes and their modules exist.
            Class.forName("javafx.application.Platform", false, loader);
            Class.forName("javafx.scene.control.TextField", false, loader);
            Class.forName("org.kordamp.ikonli.javafx.FontIcon", false, loader);
            // These do initialize for real, proving the runtime and the icon pack are usable.
            Class.forName("javafx.application.Platform", true, loader);
            Class.forName("javafx.scene.image.Image", true, loader);
            Class.forName("org.kordamp.ikonli.feather.Feather", true, loader);
            return "Platform/TextField/FontIcon load; Platform, Image and the Feather icons initialize";
        }));

        checks.add(check("icons", () -> {
            String resource = "/icons/app-256.png";
            try (InputStream in = SmokeTestRunner.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException(resource + " is not on the classpath");
                }
                BufferedImage image = ImageIO.read(in);
                if (image == null || image.getWidth() != 256 || image.getHeight() != 256) {
                    throw new IllegalStateException(resource + " does not decode to 256x256");
                }
            }
            return resource + " decodes to 256x256";
        }));

        checks.add(check("file-encoding", () -> {
            String encoding = System.getProperty("file.encoding");
            if (!StandardCharsets.UTF_8.name().equalsIgnoreCase(encoding)) {
                throw new IllegalStateException("file.encoding=" + encoding
                        + " (the installer must pass -Dfile.encoding=UTF-8)");
            }
            return encoding;
        }));

        return new Report(List.copyOf(checks), List.copyOf(facts));
    }

    /** The report text: facts, then one line per check, then the result. */
    public static String format(Report report) {
        StringBuilder text = new StringBuilder();
        for (String fact : report.facts()) {
            text.append(fact).append('\n');
        }
        for (Check check : report.checks()) {
            text.append("check.").append(check.name()).append('=')
                    .append(check.ok() ? "OK" : "FAIL").append('|')
                    .append(oneLine(check.detail())).append('\n');
        }
        text.append("RESULT=").append(report.result()).append('\n');
        return text.toString();
    }

    /** Names of the checks whose outcome depends on the packaged runtime and JVM options. */
    public static List<String> runtimeDependentChecks() {
        return List.of("file-encoding");
    }

    // ------------------------------------------------------------------

    private static List<String> baseFacts(Path dbPath) {
        List<String> facts = new ArrayList<>();
        facts.add("smoke=1");
        facts.add("db=" + dbPath);
        facts.add("at=" + Instant.now());
        facts.add("java.version=" + System.getProperty("java.version"));
        facts.add("java.vendor=" + System.getProperty("java.vendor"));
        facts.add("max.heap.mb=" + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)));
        facts.add("file.encoding=" + System.getProperty("file.encoding"));
        return facts;
    }

    private static Check check(String name, Probe probe) {
        try {
            return new Check(name, true, probe.run());
        } catch (Throwable failure) {
            return new Check(name, false, describe(failure));
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static void assertMessage(String key, String expected) {
        String value = Messages.get(key);
        if (!expected.equals(value)) {
            throw new IllegalStateException(key + "=" + value);
        }
        if (value.indexOf('\uFFFD') >= 0) {
            throw new IllegalStateException(key + " contains a replacement character");
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = SmokeTestRunner.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
