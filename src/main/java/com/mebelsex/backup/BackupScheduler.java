package com.mebelsex.backup;

import com.mebelsex.settings.AppSettings;
import com.mebelsex.settings.SettingsService;
import com.mebelsex.ui.AlertHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the automatic backups:
 * (a) once per day after backup.time while the app runs,
 * (b) at startup when no successful backup exists for today,
 * (c) the close backup is triggered by the main window (see MebelSexApplication).
 *
 * Disabled when {@code mebelsex.backup.scheduler.enabled=false} and in any dev
 * run (MEBELSEX_SNAPSHOT_DIR / MEBELSEX_DB_PATH set), so tests and snapshots
 * never touch real folders.
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final BackupService backupService;
    private final SettingsService settingsService;
    private final boolean enabled;

    private ScheduledExecutorService executor;
    private volatile LocalDate lastScheduledDate;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    public BackupScheduler(BackupService backupService, SettingsService settingsService,
            @Value("${mebelsex.backup.scheduler.enabled:true}") boolean enabled) {
        this.backupService = backupService;
        this.settingsService = settingsService;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && !devEnvironment();
    }

    @PostConstruct
    void start() {
        if (!isEnabled()) {
            log.info("Backup scheduler disabled");
            return;
        }
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "backup-scheduler");
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newSingleThreadScheduledExecutor(factory);
        executor.schedule(this::startupRun, 5, TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::tick, 60, 60, TimeUnit.SECONDS);
        log.info("Backup scheduler started");
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void startupRun() {
        try {
            AppSettings settings = settingsService.load();
            if (!settings.backupAutoEnabled()) {
                return;
            }
            LocalDate last = settings.backupLastSuccess() == null ? null
                    : settings.backupLastSuccess().toLocalDate();
            if (BackupPolicy.shouldRunStartup(LocalDate.now(), last)) {
                runAuto(BackupReason.STARTUP);
                lastScheduledDate = LocalDate.now();
            }
        } catch (Exception e) {
            handleFailure(e);
        }
    }

    private void tick() {
        try {
            AppSettings settings = settingsService.load();
            LocalTime time = parseTime(settings.backupTime());
            if (BackupPolicy.shouldRunScheduled(LocalDateTime.now(), time, lastScheduledDate,
                    settings.backupAutoEnabled())) {
                runAuto(BackupReason.SCHEDULED);
                lastScheduledDate = LocalDate.now();
            }
        } catch (Exception e) {
            handleFailure(e);
        }
    }

    private void runAuto(BackupReason reason) {
        try {
            backupService.createBackup(reason);
        } catch (Exception e) {
            handleFailure(e);
        }
    }

    private void handleFailure(Exception e) {
        log.error("Automatic backup failed", e);
        try {
            settingsService.saveAttemptError(e.getMessage());
        } catch (RuntimeException ignored) {
            // do not let the error handler throw
        }
        if (warned.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                try {
                    ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
                    AlertHelper.error(bundle.getString("backup.notify.title"), bundle.getString("backup.notify.text"));
                } catch (RuntimeException ignored) {
                    // no UI available (headless); log only
                }
            });
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FMT);
        } catch (RuntimeException e) {
            return LocalTime.MIDNIGHT;
        }
    }

    private static boolean devEnvironment() {
        return notBlank(System.getenv("MEBELSEX_SNAPSHOT_DIR")) || notBlank(System.getenv("MEBELSEX_DB_PATH"));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
