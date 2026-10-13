package com.mebelsex.release;

/**
 * The single place where the build-time release tools write to the console.
 *
 * <p>
 * The application itself logs through SLF4J and {@code NoConsoleOutputTest} forbids console
 * output under {@code src/main} (the two dev-only snapshot tools are already allow-listed).
 * The release tools are not part of the running application: they are started from
 * {@code installer\build-installer.cmd} where a Maven build has just finished, so adding
 * logback to their classpath only to see a progress line is pointless. Their console output
 * lives here; the test allow-lists this one file by name.
 */
public final class ConsoleOutput {

    private ConsoleOutput() {
    }

    /** One line on the standard output. */
    public static void line(String message) {
        System.out.println(message);
        System.out.flush();
    }

    /** One line on the standard error. */
    public static void error(String message) {
        System.err.println(message);
        System.err.flush();
    }
}
