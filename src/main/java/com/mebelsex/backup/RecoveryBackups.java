package com.mebelsex.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pure finder for recent valid backup files used by the startup recovery dialog.
 * Searches the given directories (up to 3 levels deep) and only keeps files that
 * {@link BackupFileInspector} accepts.
 */
public final class RecoveryBackups {

    public record Candidate(Path file, LocalDateTime time, long size,
            long customers, long orders, long payments) {
    }

    private RecoveryBackups() {
    }

    public static List<Candidate> findRecent(List<Path> searchDirs, int limit) {
        Set<Path> seen = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        if (searchDirs != null) {
            for (Path dir : searchDirs) {
                collect(dir, seen, candidates);
            }
        }
        candidates.sort(Comparator.comparing(Candidate::time).reversed());
        if (candidates.size() > limit) {
            return new ArrayList<>(candidates.subList(0, limit));
        }
        return candidates;
    }

    private static void collect(Path dir, Set<Path> seen, List<Candidate> candidates) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir, 3)) {
            for (Path file : walk.toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase().endsWith(".db")) {
                    continue;
                }
                Path absolute = file.toAbsolutePath().normalize();
                if (!seen.add(absolute)) {
                    continue;
                }
                BackupFileInspector.Inspection inspection = BackupFileInspector.inspect(absolute, null);
                if (!inspection.valid()) {
                    continue;
                }
                candidates.add(new Candidate(absolute, lastModified(absolute), size(absolute),
                        inspection.customers(), inspection.orders(), inspection.payments()));
            }
        } catch (IOException ignored) {
            // unreadable folder: skip it
        }
    }

    private static LocalDateTime lastModified(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }
}
