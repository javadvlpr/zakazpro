package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PendingRestoreApplierTest {

    @TempDir
    Path dir;

    @Test
    void returnsNullWhenNothingPending() throws IOException {
        Files.writeString(dir.resolve(PendingRestoreApplier.DB_NAME), "live");
        assertNull(PendingRestoreApplier.apply(dir));
        assertNull(PendingRestoreApplier.readAndClearResult(dir));
    }

    @Test
    void swapSucceedsAndKeepsReplacedCopy() throws IOException {
        Files.writeString(dir.resolve(PendingRestoreApplier.DB_NAME), "OLD");
        Files.writeString(dir.resolve(PendingRestoreApplier.DB_NAME + "-wal"), "OLDWAL");
        Files.writeString(dir.resolve(PendingRestoreApplier.PENDING_NAME), "NEW");
        Files.writeString(dir.resolve(PendingRestoreApplier.PENDING_MARKER), "source");

        RestoreApplyResult result = PendingRestoreApplier.apply(dir);

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("NEW", Files.readString(dir.resolve(PendingRestoreApplier.DB_NAME)));
        assertFalse(Files.exists(dir.resolve(PendingRestoreApplier.PENDING_NAME)));
        assertFalse(Files.exists(dir.resolve(PendingRestoreApplier.PENDING_MARKER)));

        List<Path> replaced = listReplaced();
        assertFalse(replaced.isEmpty(), "a replaced safety copy must exist");
        assertTrue(replaced.stream().anyMatch(p -> readOrEmpty(p).equals("OLD")));

        RestoreApplyResult marker = PendingRestoreApplier.readAndClearResult(dir);
        assertNotNull(marker);
        assertTrue(marker.success());
        assertFalse(Files.exists(dir.resolve(PendingRestoreApplier.RESULT_NAME)));
    }

    @Test
    void failureKeepsLiveDbAndWritesFailureMarker() throws IOException {
        Files.writeString(dir.resolve(PendingRestoreApplier.DB_NAME), "OLD");
        Files.writeString(dir.resolve(PendingRestoreApplier.PENDING_NAME), "NEW");
        // Block the replaced/ folder so the swap cannot start.
        Files.writeString(dir.resolve(PendingRestoreApplier.REPLACED_DIR), "not a directory");

        RestoreApplyResult result = PendingRestoreApplier.apply(dir);

        assertNotNull(result);
        assertFalse(result.success());
        assertEquals("OLD", Files.readString(dir.resolve(PendingRestoreApplier.DB_NAME)));
        assertTrue(Files.exists(dir.resolve(PendingRestoreApplier.PENDING_NAME)));

        RestoreApplyResult marker = PendingRestoreApplier.readAndClearResult(dir);
        assertNotNull(marker);
        assertFalse(marker.success());
        assertNotNull(marker.detail());
    }

    private List<Path> listReplaced() throws IOException {
        Path replaced = dir.resolve(PendingRestoreApplier.REPLACED_DIR);
        if (!Files.isDirectory(replaced)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(replaced)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("mebel_replaced_"))
                    .collect(Collectors.toList());
        }
    }

    private static String readOrEmpty(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }
}
