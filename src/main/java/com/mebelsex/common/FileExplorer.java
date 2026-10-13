package com.mebelsex.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Best-effort Windows Explorer helper (pure Java). Failures are logged and ignored
 * so a missing shell never breaks the app.
 */
public final class FileExplorer {

    private static final Logger log = LoggerFactory.getLogger(FileExplorer.class);

    private FileExplorer() {
    }

    /** Opens the folder itself. */
    public static void openFolder(Path folder) {
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
            // best effort
        }
        run("explorer.exe", folder.toAbsolutePath().toString());
    }

    /** Opens the folder and selects the given file. */
    public static void selectFile(Path file) {
        if (file == null) {
            return;
        }
        run("explorer.exe", "/select," + file.toAbsolutePath());
    }

    private static void run(String... command) {
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.warn("Cannot open Explorer: {}", e.getMessage());
        }
    }
}
