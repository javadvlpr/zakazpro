package com.mebelsex.settings;

import com.mebelsex.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Typed access to the key/value settings table. Missing keys are created lazily
 * with defaults; no Flyway migration is needed for new keys.
 *
 * Writes go through @Transactional methods. Reads fall back to sensible defaults.
 */
@Service
public class SettingsService {

    public static final String KEY_COMPANY_NAME = "company.name";
    public static final String KEY_COMPANY_PHONE = "company.phone";
    public static final String KEY_COMPANY_ADDRESS = "company.address";
    public static final String KEY_BACKUP_FOLDER = "backup.folder";
    public static final String KEY_BACKUP_AUTO = "backup.auto.enabled";
    public static final String KEY_BACKUP_TIME = "backup.time";
    public static final String KEY_BACKUP_ON_CLOSE = "backup.on_close";
    public static final String KEY_BACKUP_KEEP_DAYS = "backup.keep_days";
    public static final String KEY_BACKUP_LAST_SUCCESS = "backup.last_success";
    public static final String KEY_BACKUP_LAST_ERROR = "backup.last_attempt_error";
    public static final String KEY_THEME = "ui.theme";
    public static final String KEY_WINDOW_X = "ui.window.x";
    public static final String KEY_WINDOW_Y = "ui.window.y";
    public static final String KEY_WINDOW_WIDTH = "ui.window.width";
    public static final String KEY_WINDOW_HEIGHT = "ui.window.height";
    public static final String KEY_WINDOW_MAXIMIZED = "ui.window.maximized";
    public static final String KEY_LICENSE_LAST_SEEN = "license.last_seen";

    public static final String DEFAULT_THEME = "LIGHT";
    public static final String DEFAULT_TIME = "00:00";
    public static final int DEFAULT_KEEP_DAYS = 60;
    public static final int MAX_KEEP_DAYS = 3650;

    private static final Pattern TIME_PATTERN = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final SettingRepository repository;

    public SettingsService(SettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AppSettings load() {
        return new AppSettings(
                get(KEY_COMPANY_NAME, ""),
                get(KEY_COMPANY_PHONE, ""),
                get(KEY_COMPANY_ADDRESS, ""),
                get(KEY_BACKUP_FOLDER, ""),
                getBoolean(KEY_BACKUP_AUTO, true),
                get(KEY_BACKUP_TIME, DEFAULT_TIME),
                getBoolean(KEY_BACKUP_ON_CLOSE, true),
                getInt(KEY_BACKUP_KEEP_DAYS, DEFAULT_KEEP_DAYS),
                getTimestamp(KEY_BACKUP_LAST_SUCCESS),
                get(KEY_BACKUP_LAST_ERROR, ""));
    }

    @Transactional
    public void saveCompany(String name, String phone, String address) {
        put(KEY_COMPANY_NAME, name);
        put(KEY_COMPANY_PHONE, phone);
        put(KEY_COMPANY_ADDRESS, address);
    }

    @Transactional
    public void saveBackup(String folder, boolean autoEnabled, String time, boolean onClose, int keepDays) {
        if (time == null || !TIME_PATTERN.matcher(time.trim()).matches()) {
            throw new BusinessException("settings.error.time_format");
        }
        if (keepDays < 0 || keepDays > MAX_KEEP_DAYS) {
            throw new BusinessException("settings.error.keep_days");
        }
        put(KEY_BACKUP_FOLDER, folder == null ? "" : folder.trim());
        put(KEY_BACKUP_AUTO, Boolean.toString(autoEnabled));
        put(KEY_BACKUP_TIME, time.trim());
        put(KEY_BACKUP_ON_CLOSE, Boolean.toString(onClose));
        put(KEY_BACKUP_KEEP_DAYS, Integer.toString(keepDays));
    }

    /** Stored theme name; callers parse it (unknown values fall back to LIGHT). */
    @Transactional(readOnly = true)
    public String loadTheme() {
        return get(KEY_THEME, DEFAULT_THEME);
    }

    @Transactional
    public void saveTheme(String theme) {
        put(KEY_THEME, theme == null || theme.isBlank() ? DEFAULT_THEME : theme);
    }

    /** Stored main-window geometry (any field null when never saved). */
    @Transactional(readOnly = true)
    public WindowState loadWindow() {
        return new WindowState(getIntOrNull(KEY_WINDOW_X), getIntOrNull(KEY_WINDOW_Y),
                getIntOrNull(KEY_WINDOW_WIDTH), getIntOrNull(KEY_WINDOW_HEIGHT),
                getBoolean(KEY_WINDOW_MAXIMIZED, false));
    }

    @Transactional
    public void saveWindow(WindowState state) {
        put(KEY_WINDOW_X, state.x() == null ? "" : Integer.toString(state.x()));
        put(KEY_WINDOW_Y, state.y() == null ? "" : Integer.toString(state.y()));
        put(KEY_WINDOW_WIDTH, state.width() == null ? "" : Integer.toString(state.width()));
        put(KEY_WINDOW_HEIGHT, state.height() == null ? "" : Integer.toString(state.height()));
        put(KEY_WINDOW_MAXIMIZED, Boolean.toString(state.maximized()));
    }

    /** Last license check time used by the clock guard; null when never stored. */
    @Transactional(readOnly = true)
    public Instant loadLicenseLastSeen() {
        String value = get(KEY_LICENSE_LAST_SEEN, null);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Transactional
    public void saveLicenseLastSeen(Instant instant) {
        put(KEY_LICENSE_LAST_SEEN, instant == null ? "" : instant.toString());
    }

    @Transactional
    public void saveLastSuccess(LocalDateTime time) {
        put(KEY_BACKUP_LAST_SUCCESS, time == null ? "" : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Transactional
    public void saveAttemptError(String error) {
        put(KEY_BACKUP_LAST_ERROR, error == null ? "" : error);
    }

    /** Backup folder from settings, or the default under the user's home. */
    @Transactional(readOnly = true)
    public Path backupFolder() {
        String value = get(KEY_BACKUP_FOLDER, "");
        if (value.isBlank()) {
            return defaultBackupFolder();
        }
        return Paths.get(value);
    }

    public Path defaultBackupFolder() {
        return Paths.get(System.getProperty("user.home"), "Documents",
                com.mebelsex.common.AppInfo.DOCUMENTS_DIR_NAME, "Backup");
    }

    // ------------------------------------------------------------------

    private String get(String key, String defaultValue) {
        return repository.findByKey(key)
                .map(Setting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private Integer getIntOrNull(String key) {
        String value = get(key, null);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private LocalDateTime getTimestamp(String key) {
        String value = get(key, null);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void put(String key, String value) {
        Setting setting = repository.findByKey(key).orElseGet(() -> new Setting(key, null));
        setting.setValue(value == null ? "" : value);
        repository.save(setting);
    }
}
