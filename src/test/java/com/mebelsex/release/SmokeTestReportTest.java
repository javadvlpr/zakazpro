package com.mebelsex.release;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the smoke-result.txt format. */
class SmokeTestReportTest {

    @Test
    void aPassingRunEndsWithResultOk() {
        SmokeTestRunner.Report report = new SmokeTestRunner.Report(
                List.of(new SmokeTestRunner.Check("flyway", true, "2 migrations applied"),
                        new SmokeTestRunner.Check("icons", true, "decodes to 256x256")),
                List.of("smoke=1", "file.encoding=UTF-8"));

        assertEquals("OK", report.result());
        assertTrue(report.ok());
        String text = SmokeTestRunner.format(report);
        assertEquals("smoke=1\nfile.encoding=UTF-8\n"
                + "check.flyway=OK|2 migrations applied\n"
                + "check.icons=OK|decodes to 256x256\n"
                + "RESULT=OK\n", text);
    }

    @Test
    void aFailingRunNamesTheFirstFailureAndKeepsEveryCheck() {
        SmokeTestRunner.Report report = new SmokeTestRunner.Report(
                List.of(new SmokeTestRunner.Check("flyway", true, "2 migrations applied"),
                        new SmokeTestRunner.Check("uzbek-text", false, "IllegalStateException: month=October"),
                        new SmokeTestRunner.Check("icons", true, "decodes to 256x256")),
                List.of("smoke=1"));

        assertFalse(report.ok());
        assertEquals("FAIL uzbek-text: IllegalStateException: month=October", report.result());
        assertEquals(List.of("uzbek-text"), report.failedChecks());
        String text = SmokeTestRunner.format(report);
        assertTrue(text.startsWith("smoke=1\n"), text);
        assertTrue(text.contains("check.flyway=OK|2 migrations applied\n"), text);
        assertTrue(text.contains("check.uzbek-text=FAIL|IllegalStateException: month=October\n"), text);
        assertTrue(text.endsWith("RESULT=FAIL uzbek-text: IllegalStateException: month=October\n"), text);
    }

    @Test
    void detailsAreFlattenedToOneLine() {
        SmokeTestRunner.Report report = new SmokeTestRunner.Report(
                List.of(new SmokeTestRunner.Check("x", true, "first\nsecond\r\nthird")),
                List.of());

        assertEquals("check.x=OK|first second  third\nRESULT=OK\n", SmokeTestRunner.format(report));
    }

    @Test
    void theRuntimeDependentChecksAreNamed() {
        assertTrue(SmokeTestRunner.runtimeDependentChecks().contains("file-encoding"));
    }
}
