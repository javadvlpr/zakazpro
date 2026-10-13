package com.mebelsex.common;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pure diagnostics ZIP builder (no Spring, no JavaFX). Takes the output directory,
 * the log directory and the info lines as parameters so tests use temp folders.
 * Never includes database or backup files.
 */
public final class DiagnosticsBuilder {

    public static final String INFO_ENTRY = "info.txt";
    public static final int DEFAULT_MAX_LOGS = 3;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final long MAX_LOG_BYTES = 5L * 1024L * 1024L;

    private DiagnosticsBuilder() {
    }

    /**
     * Writes {@code zakazpro-diagnostics-<date-time>.zip} into {@code outputDir}, containing
     * the newest {@value #DEFAULT_MAX_LOGS} log files and the given info lines, then
     * returns the created file.
     */
    public static Path create(Path outputDir, Path logDir, List<String> infoLines) throws IOException {
        Files.createDirectories(outputDir);
        Path zip = outputDir.resolve(AppInfo.LOG_FILE_BASENAME + "-diagnostics-"
                + LocalDateTime.now().format(STAMP) + ".zip");
        List<Path> logs = recentLogs(logDir, DEFAULT_MAX_LOGS);

        try (OutputStream out = Files.newOutputStream(zip);
                ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            StringBuilder info = new StringBuilder();
            if (infoLines != null) {
                for (String line : infoLines) {
                    info.append(line).append(System.lineSeparator());
                }
            }
            zos.putNextEntry(new ZipEntry(INFO_ENTRY));
            zos.write(info.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            for (Path log : logs) {
                zos.putNextEntry(new ZipEntry("logs/" + log.getFileName()));
                zos.write(readCapped(log));
                zos.closeEntry();
            }
        }
        return zip;
    }

    /** Newest regular .log files, most recent first. Never a .db/.zip. */
    static List<Path> recentLogs(Path logDir, int max) {
        List<Path> logs = new ArrayList<>();
        if (logDir == null || !Files.isDirectory(logDir)) {
            return logs;
        }
        try (var stream = Files.newDirectoryStream(logDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase();
                if (Files.isRegularFile(file) && name.endsWith(".log")) {
                    logs.add(file);
                }
            }
        } catch (IOException e) {
            return logs;
        }
        logs.sort(Comparator.comparingLong(DiagnosticsBuilder::lastModified).reversed());
        if (logs.size() > max) {
            return new ArrayList<>(logs.subList(0, max));
        }
        return logs;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static byte[] readCapped(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= MAX_LOG_BYTES) {
            return Files.readAllBytes(file);
        }
        try (var in = Files.newInputStream(file)) {
            return in.readNBytes((int) MAX_LOG_BYTES);
        }
    }
}
