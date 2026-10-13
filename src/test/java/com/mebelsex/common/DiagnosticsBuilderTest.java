package com.mebelsex.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void writesLastThreeLogsAndInfoButNoDatabase() throws Exception {
        Path logDir = tempDir.resolve("logs");
        Files.createDirectories(logDir);
        for (int i = 0; i < 4; i++) {
            Path log = logDir.resolve("mebelsex." + i + ".log");
            Files.writeString(log, "line " + i);
            Files.setLastModifiedTime(log, FileTime.fromMillis(1_000L + i * 10_000L));
        }
        Files.writeString(logDir.resolve("mebel_2026-01-01.db"), "SQLite format 3");
        Files.writeString(logDir.resolve("notes.txt"), "ignore me");

        Path outDir = tempDir.resolve("out");
        Path zip = DiagnosticsBuilder.create(outDir, logDir,
                List.of("Versiya: 0.0.1", "Java: 17"));

        assertTrue(Files.isRegularFile(zip), "zip must be created");
        assertEquals(outDir, zip.getParent());

        Set<String> entries = entries(zip);
        assertTrue(entries.contains("info.txt"));
        assertTrue(entries.contains("logs/mebelsex.3.log"));
        assertTrue(entries.contains("logs/mebelsex.2.log"));
        assertTrue(entries.contains("logs/mebelsex.1.log"));
        assertFalse(entries.contains("logs/mebelsex.0.log"), "only the 3 newest logs");
        assertTrue(entries.stream().noneMatch(e -> e.toLowerCase().endsWith(".db")),
                "the database must never be included");
        assertTrue(entries.stream().noneMatch(e -> e.contains("mebel_2026")),
                "backup/database files must never be included");
    }

    @Test
    void infoEntryContainsTheGivenLines() throws Exception {
        Path logDir = tempDir.resolve("logs");
        Files.createDirectories(logDir);
        Files.writeString(logDir.resolve("mebelsex.log"), "hello");

        Path zip = DiagnosticsBuilder.create(tempDir.resolve("out"), logDir,
                List.of("Versiya: 0.0.1", "Java: 17"));
        String info = readEntry(zip, "info.txt");
        assertTrue(info.contains("Versiya: 0.0.1"));
        assertTrue(info.contains("Java: 17"));
    }

    @Test
    void missingLogDirStillProducesInfoOnly() throws Exception {
        Path zip = DiagnosticsBuilder.create(tempDir.resolve("out"), tempDir.resolve("nope"),
                List.of("Sana: x"));
        Set<String> entries = entries(zip);
        assertEquals(Set.of("info.txt"), entries);
    }

    private static Set<String> entries(Path zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipFile file = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> e = file.entries();
            while (e.hasMoreElements()) {
                names.add(e.nextElement().getName());
            }
        }
        return names;
    }

    private static String readEntry(Path zip, String name) throws Exception {
        try (ZipFile file = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = file.getEntry(name);
            try (InputStream in = file.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
