package com.mebelsex.license;

import java.nio.file.Path;

/**
 * The three license-related files that live next to the database file, so every
 * installation (and every dev/test temp DB) keeps its own license.
 *
 * @param licenseFile {@code license.lic}
 * @param backupFile  {@code license.lic.bak} (the previous license)
 * @param stateFile   {@code license.state} (last-seen timestamp for the clock guard)
 */
public record LicensePaths(Path licenseFile, Path backupFile, Path stateFile) {

    public static final String LICENSE_FILE_NAME = "license.lic";
    public static final String BACKUP_FILE_NAME = "license.lic.bak";
    public static final String STATE_FILE_NAME = "license.state";

    public static LicensePaths forDatabaseDirectory(Path databaseDirectory) {
        return new LicensePaths(
                databaseDirectory.resolve(LICENSE_FILE_NAME),
                databaseDirectory.resolve(BACKUP_FILE_NAME),
                databaseDirectory.resolve(STATE_FILE_NAME));
    }
}
