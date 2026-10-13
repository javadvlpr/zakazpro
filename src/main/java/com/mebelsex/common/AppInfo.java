package com.mebelsex.common;

/**
 * Single source for the VISIBLE product name, the data/log/backup folder and file names and
 * the app version. Only visible names live here.
 *
 * <p>
 * Internal identifiers must never be renamed (they would break existing installs and licenses):
 * the Java packages {@code com.mebelsex}, the license header {@code MEBELSEX-LICENSE-1}, the key
 * file headers, the Machine ID salt {@code mebelsex-machine-v1:}, the database file name
 * {@code mebel.db} and the restore/replace marker names inside the data folder.
 */
public final class AppInfo {

    /** Visible product name. */
    public static final String PRODUCT_NAME = "ZakazPro";

    /** ASCII slug used for folders and files; must stay a valid Windows folder name. */
    public static final String SLUG = "ZakazPro";

    /** App data folder under {@code %APPDATA%}. */
    public static final String DATA_DIR_NAME = "ZakazPro";

    /** Documents folder used for the default backup and diagnostics folders. */
    public static final String DOCUMENTS_DIR_NAME = "ZakazPro";

    /** Base name of the active log file (logback-spring.xml hardcodes this value). */
    public static final String LOG_FILE_BASENAME = "zakazpro";

    /** Prefix of backup files, e.g. {@code zakazpro_2026-10-06_10-00.db}. */
    public static final String BACKUP_FILE_PREFIX = "zakazpro_";

    /** App version; kept equal to the Maven project version (AppInfoTest reads pom.xml). */
    public static final String VERSION = "1.0.0";

    private AppInfo() {
    }

    /** Runtime version (jar manifest when packaged, otherwise {@link #VERSION}). */
    public static String version() {
        Package pkg = AppInfo.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isBlank() ? VERSION : version;
    }

    /** Window title: {@code "<company> — ZakazPro"}, or just {@code "ZakazPro"} when blank. */
    public static String windowTitle(String companyNameOrBlank) {
        if (companyNameOrBlank == null || companyNameOrBlank.isBlank()) {
            return PRODUCT_NAME;
        }
        return companyNameOrBlank.trim() + " \u2014 " + PRODUCT_NAME;
    }
}
