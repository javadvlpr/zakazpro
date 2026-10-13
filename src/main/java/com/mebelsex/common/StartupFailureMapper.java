package com.mebelsex.common;

/**
 * Pure mapper: turns an exception thrown during Spring/Flyway boot into the message
 * key of a short, user-friendly Uzbek reason. No Spring, no JavaFX; the whole cause
 * chain is inspected by message text so no Flyway type is imported.
 */
public final class StartupFailureMapper {

    /** Flyway found a schema created by a newer app version. */
    public static final String NEWER_SCHEMA_KEY = "startup.failed.flyway_newer";
    /** The database file is locked by another process. */
    public static final String DB_LOCKED_KEY = "startup.failed.db_locked";
    /** Fallback reason. */
    public static final String GENERIC_KEY = "startup.failed.reason";

    private StartupFailureMapper() {
    }

    /** Returns the message key for the given throwable (never null). */
    public static String messageKey(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (message.contains("newer than the latest available migration")
                    || message.contains("newer than the latest supported")
                    || message.contains("schema version")
                    || message.contains("not resolved locally")) {
                return NEWER_SCHEMA_KEY;
            }
            if (message.contains("sqlite_busy") || message.contains("database is locked")
                    || message.contains("database table is locked")) {
                return DB_LOCKED_KEY;
            }
        }
        return GENERIC_KEY;
    }
}
