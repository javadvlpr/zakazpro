package com.mebelsex.release;

/**
 * Pure helper that turns a Maven project version (e.g. {@code 1.0.0-SNAPSHOT}) into the
 * numeric form jpackage accepts on Windows.
 *
 * <p>
 * jpackage (Windows exe/msi) requires {@code major.minor.build}: at most three numeric
 * parts, each between 0 and 65535, with the major part greater than zero. Missing parts are
 * padded with {@code 0} and any pre-release/build suffix (everything from the first
 * {@code -} or {@code +}) is dropped. Anything else is rejected.
 *
 * <p>
 * No Spring, no I/O: unit-testable and usable from the packaging driver.
 */
public final class InstallerVersion {

    /** jpackage rejects a part above this value on Windows. */
    static final int MAX_PART = 65535;

    private InstallerVersion() {
    }

    /**
     * @return the numeric version, always three dot-separated parts
     * @throws IllegalArgumentException when {@code raw} is null/blank/not a valid version
     */
    public static String normalizeInstallerVersion(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Version is missing");
        }
        String version = raw.trim();
        int cut = firstSuffix(version);
        if (cut >= 0) {
            version = version.substring(0, cut);
        }
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Not a version: " + raw.trim());
        }
        String[] parts = version.split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Version has more than three parts: " + raw.trim());
        }
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = parsePart(parts[i], raw.trim());
        }
        if (numbers[0] < 1) {
            throw new IllegalArgumentException("Version major part must be greater than zero: " + raw.trim());
        }
        return numbers[0] + "." + numbers[1] + "." + numbers[2];
    }

    /** Index of the first {@code -} or {@code +}, or -1. */
    private static int firstSuffix(String version) {
        int dash = version.indexOf('-');
        int plus = version.indexOf('+');
        if (dash < 0) {
            return plus;
        }
        if (plus < 0) {
            return dash;
        }
        return Math.min(dash, plus);
    }

    private static int parsePart(String part, String raw) {
        if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Version part is not numeric: " + raw);
        }
        if (part.length() > 1 && part.charAt(0) == '0') {
            throw new IllegalArgumentException("Version part has a leading zero: " + raw);
        }
        int value;
        try {
            value = Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version part is too large: " + raw);
        }
        if (value > MAX_PART) {
            throw new IllegalArgumentException("Version part is above " + MAX_PART + ": " + raw);
        }
        return value;
    }
}
