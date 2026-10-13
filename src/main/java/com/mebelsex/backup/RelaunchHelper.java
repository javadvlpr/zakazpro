package com.mebelsex.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort self relaunch after a restore is scheduled. Returns false when the
 * command line is not available, so the UI can tell the user to reopen manually.
 */
public final class RelaunchHelper {

    private static final Logger log = LoggerFactory.getLogger(RelaunchHelper.class);

    private RelaunchHelper() {
    }

    public static boolean relaunch() {
        var info = ProcessHandle.current().info();
        if (info.command().isEmpty()) {
            return false;
        }
        List<String> command = new ArrayList<>();
        command.add(info.command().get());
        info.arguments().ifPresent(args -> {
            for (String arg : args) {
                command.add(arg);
            }
        });
        // Marker read by the Launcher: wait for the old process to release the file lock.
        command.add("--relaunch");
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception e) {
            log.warn("Cannot relaunch the application", e);
            return false;
        }
    }
}
