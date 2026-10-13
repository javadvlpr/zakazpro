package com.mebelsex.release;

import com.mebelsex.common.AppInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the --smoke-test environment guard (no Spring, no JavaFX). */
class SmokeTestGuardTest {

    @TempDir
    Path temp;

    private static final String[] SMOKE = { "--smoke-test" };

    @Test
    void recognisesTheArgumentAmongOthers() {
        assertTrue(SmokeTestGuard.isSmokeTest(SMOKE));
        assertTrue(SmokeTestGuard.isSmokeTest(new String[] { "--relaunch", "--smoke-test" }));
        assertFalse(SmokeTestGuard.isSmokeTest(new String[] { "--relaunch" }));
        assertFalse(SmokeTestGuard.isSmokeTest(new String[0]));
        assertFalse(SmokeTestGuard.isSmokeTest(null));
    }

    @Test
    void aNormalStartNeedsNoEnvironment() {
        assertTrue(SmokeTestGuard.validate(new String[] { "--relaunch" }, Map.of()).ok());
        assertTrue(SmokeTestGuard.validate(new String[0], null).ok());
    }

    @Test
    void requiresTheDatabasePathVariable() {
        SmokeTestGuard.Validation missing = SmokeTestGuard.validate(SMOKE, Map.of());
        assertFalse(missing.ok());
        assertTrue(missing.message().contains("MEBELSEX_DB_PATH"), missing.message());

        SmokeTestGuard.Validation blank = SmokeTestGuard.validate(SMOKE, Map.of("MEBELSEX_DB_PATH", "  "));
        assertFalse(blank.ok());
    }

    @Test
    void rejectsARelativePath() {
        Path relative = Path.of("target", "smoke", "x.db");
        assertFalse(relative.isAbsolute());

        SmokeTestGuard.Validation result = SmokeTestGuard.validate(SMOKE,
                Map.of("MEBELSEX_DB_PATH", relative.toString()));

        assertFalse(result.ok());
        assertTrue(result.message().contains("absolute"), result.message());
    }

    @Test
    void rejectsAPathThatIsNotADatabaseFile() {
        SmokeTestGuard.Validation result = SmokeTestGuard.validate(SMOKE,
                Map.of("MEBELSEX_DB_PATH", temp.resolve("folder").toAbsolutePath().toString()));

        assertFalse(result.ok());
        assertTrue(result.message().contains(".db"), result.message());
    }

    @Test
    void refusesTheRealDataFolder() {
        Path appData = temp.resolve("AppData").toAbsolutePath();
        Path realDatabase = appData.resolve(AppInfo.DATA_DIR_NAME).resolve("mebel.db");

        SmokeTestGuard.Validation result = SmokeTestGuard.validate(SMOKE, Map.of(
                "APPDATA", appData.toString(),
                "MEBELSEX_DB_PATH", realDatabase.toString()));

        assertFalse(result.ok());
        assertTrue(result.message().contains("real data folder"), result.message());
    }

    @Test
    void acceptsAnAbsoluteTemporaryDatabase() {
        Path database = temp.resolve("smoke").resolve("smoke.db");

        SmokeTestGuard.Validation result = SmokeTestGuard.validate(SMOKE, Map.of(
                "APPDATA", temp.resolve("AppData").toAbsolutePath().toString(),
                "MEBELSEX_DB_PATH", database.toString()));

        assertTrue(result.ok(), result.message());
        assertEquals("", result.message());
    }

    @Test
    void resultFileSitsNextToTheDatabase() {
        Path database = temp.resolve("smoke").resolve("smoke.db");
        assertEquals(database.getParent().resolve(SmokeTestGuard.RESULT_FILE_NAME),
                SmokeTestGuard.resultFileFor(database));
    }
}
