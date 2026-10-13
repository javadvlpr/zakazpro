package com.mebelsex.release;

import com.mebelsex.common.AppInfo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Pure checks for the {@code --smoke-test} entry point: no file system, no Spring, no
 * JavaFX. It refuses to start unless the run is clearly a throw-away one, so the packaged
 * smoke test can never touch the owner's real database.
 *
 * <p>
 * The smoke test requires {@code MEBELSEX_DB_PATH} (an absolute {@code .db} path outside
 * the real data folder). Anything else makes the launcher print a message and exit with
 * {@link #EXIT_INVALID_ENVIRONMENT} without writing a single file.
 */
public final class SmokeTestGuard {

    /** The program argument that switches the app into smoke-test mode. */
    public static final String ARG = "--smoke-test";

    /** Environment variable the smoke test insists on. */
    public static final String ENV_DB_PATH = "MEBELSEX_DB_PATH";

    /** Exit code for a refused (misconfigured) smoke-test run. */
    public static final int EXIT_INVALID_ENVIRONMENT = 2;

    /** Result file written next to the smoke database. */
    public static final String RESULT_FILE_NAME = "smoke-result.txt";

    private SmokeTestGuard() {
    }

    /** True when the launcher was started with {@link #ARG}. */
    public static boolean isSmokeTest(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (ARG.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates a smoke-test run.
     *
     * @return {@link Validation#pass()} for a normal (non-smoke) start and for a valid
     *         smoke-test environment; otherwise a failure with the reason to print
     */
    public static Validation validate(String[] args, Map<String, String> env) {
        if (!isSmokeTest(args)) {
            return Validation.pass();
        }
        String raw = env == null ? null : env.get(ENV_DB_PATH);
        if (raw == null || raw.isBlank()) {
            return Validation.fail(ENV_DB_PATH + " is required for " + ARG);
        }
        String trimmed = raw.trim();
        Path path = Paths.get(trimmed);
        if (!path.isAbsolute()) {
            return Validation.fail(ENV_DB_PATH + " must be an absolute path: " + trimmed);
        }
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".db")) {
            return Validation.fail(ENV_DB_PATH + " must point to a .db file: " + trimmed);
        }
        if (isInsideRealDataFolder(path, env)) {
            return Validation.fail("refusing to run " + ARG + " against the real data folder: " + trimmed);
        }
        return Validation.pass();
    }

    /** The result file that belongs to a smoke database. */
    public static Path resultFileFor(Path dbPath) {
        Path parent = dbPath.getParent();
        return parent == null ? Paths.get(RESULT_FILE_NAME) : parent.resolve(RESULT_FILE_NAME);
    }

    /** True when the path sits under {@code %APPDATA%\<data dir>}. */
    private static boolean isInsideRealDataFolder(Path path, Map<String, String> env) {
        String appData = env == null ? null : env.get("APPDATA");
        if (appData == null || appData.isBlank()) {
            return false;
        }
        Path dataDir = Paths.get(appData, AppInfo.DATA_DIR_NAME).toAbsolutePath().normalize();
        return path.normalize().startsWith(dataDir);
    }

    /** Outcome of {@link #validate(String[], Map)}. */
    public record Validation(boolean ok, String message) {

        public static Validation pass() {
            return new Validation(true, "");
        }

        public static Validation fail(String message) {
            return new Validation(false, message);
        }
    }
}
