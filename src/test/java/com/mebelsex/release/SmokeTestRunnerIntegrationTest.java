package com.mebelsex.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the smoke-test checks against a real (temporary) database in the normal Spring
 * context. The checks that depend on the packaged runtime and its JVM options (the default
 * encoding) are only reported here; the packaging driver asserts them against the real
 * app-image.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-zakazpro-smoke-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class SmokeTestRunnerIntegrationTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @TempDir
    Path tempDir;

    @Test
    void everyEnvironmentIndependentCheckPasses() {
        SmokeTestRunner.Report report = SmokeTestRunner.inspect(context, tempDir.resolve("smoke.db"));

        assertFalse(report.checks().isEmpty());
        List<String> runtimeDependent = SmokeTestRunner.runtimeDependentChecks();

        List<String> unexpected = report.checks().stream()
                .filter(check -> !check.ok() && !runtimeDependent.contains(check.name()))
                .map(check -> check.name() + " -> " + check.detail())
                .toList();
        assertTrue(unexpected.isEmpty(), () -> "failing checks: " + unexpected);

        for (String name : runtimeDependent) {
            assertTrue(report.checks().stream().anyMatch(check -> check.name().equals(name)),
                    "the runtime-dependent check " + name + " must still be reported");
        }

        assertTrue(report.facts().stream().anyMatch(fact -> fact.startsWith("java.version=")), "java.version");
        assertTrue(report.facts().stream().anyMatch(fact -> fact.startsWith("max.heap.mb=")), "max.heap.mb");
        assertTrue(report.facts().stream().anyMatch(fact -> fact.startsWith("machine.id=MX-")), "machine.id");
        assertTrue(report.facts().stream().anyMatch(fact -> fact.startsWith("license.state=")), "license.state");
    }

    @Test
    void theReportIsWrittenToTheResultFile() throws Exception {
        Path directory = tempDir.resolve("out");
        java.nio.file.Files.createDirectories(directory);
        Path database = directory.resolve("smoke.db");
        Path resultFile = SmokeTestGuard.resultFileFor(database);

        SmokeTestRunner.Report report = SmokeTestRunner.inspect(context, database);
        java.nio.file.Files.writeString(resultFile, SmokeTestRunner.format(report));

        String text = java.nio.file.Files.readString(resultFile);
        assertTrue(text.contains("check.flyway=OK|"), text);
        assertTrue(text.trim().endsWith("RESULT=" + report.result()), text);
    }
}
