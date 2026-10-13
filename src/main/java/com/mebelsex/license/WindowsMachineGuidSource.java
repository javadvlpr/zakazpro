package com.mebelsex.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Windows MachineGuid from the registry using the fixed full path to
 * {@code %SystemRoot%\System32\reg.exe}. The process output is read on a daemon
 * thread and the process is destroyed if it does not finish within 5 seconds,
 * so a missing or hanging {@code reg.exe} can never block the caller.
 *
 * <p>The output parsing is a pure static method so it can be unit-tested on any OS.
 */
public final class WindowsMachineGuidSource implements MachineIdSource {

    private static final long TIMEOUT_SECONDS = 5L;

    private static final String REG_KEY = "HKLM\\SOFTWARE\\Microsoft\\Cryptography";

    private static final Pattern GUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Path regExe;

    public WindowsMachineGuidSource() {
        this(defaultRegExe());
    }

    public WindowsMachineGuidSource(Path regExe) {
        this.regExe = regExe;
    }

    private static Path defaultRegExe() {
        String root = System.getenv("SystemRoot");
        if (root == null || root.isBlank()) {
            root = System.getenv("windir");
        }
        if (root == null || root.isBlank()) {
            root = "C:\\Windows";
        }
        return Path.of(root, "System32", "reg.exe");
    }

    @Override
    public String readRawId() throws MachineIdException {
        ProcessBuilder builder = new ProcessBuilder(
                regExe.toString(), "query", REG_KEY, "/v", "MachineGuid", "/reg:64");
        builder.redirectErrorStream(true);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new MachineIdException("reg.exe ishga tushmadi: " + regExe, e);
        }

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                output.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // The process was destroyed; whatever was read stays in the buffer.
            }
        }, "mebelsex-reg-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MachineIdException("reg.exe " + TIMEOUT_SECONDS + " soniyada javob bermadi");
            }
            reader.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new MachineIdException("reg.exe kutish uzildi", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return parseMachineGuid(output.toString());
    }

    /**
     * Extracts the MachineGuid value from {@code reg query} output.
     * Pure and OS-independent so it is unit-tested directly.
     *
     * @param output the merged stdout/stderr of the reg command, may be null
     * @return the GUID as printed
     * @throws MachineIdException when no GUID is present
     */
    public static String parseMachineGuid(String output) throws MachineIdException {
        if (output == null) {
            throw new MachineIdException("reg.exe chiqishi bo'sh");
        }
        for (String line : output.split("\\R")) {
            if (line.toLowerCase(Locale.ROOT).contains("machineguid")) {
                Matcher matcher = GUID.matcher(line);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        throw new MachineIdException("MachineGuid qiymati topilmadi");
    }
}
