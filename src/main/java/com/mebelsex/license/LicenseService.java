package com.mebelsex.license;

import com.mebelsex.common.BusinessException;
import com.mebelsex.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads and evaluates the offline license, imports new licenses and re-checks
 * periodically. All the verification logic lives in the {@code com.mebelsex.license}
 * core classes; this service only handles files, settings, caching and listeners.
 *
 * <p>The license file lives next to the database, so dev/test temp DBs never touch
 * an installed license. Nothing here ever writes business tables.
 */
public class LicenseService implements LicenseGate {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    /** Developer bypass flag; never exposed through env vars or Spring properties. */
    public static final String DEV_FLAG = "mebelsex.license.dev";
    /** Optional dev state: BLOCKED | READ_ONLY | EXPIRING | VALID. */
    public static final String FORCE_PROPERTY = "mebelsex.license.force";

    private static final long RECHECK_PERIOD_MINUTES = 30L;
    private static final long FIRST_RECHECK_DELAY_SECONDS = 5L;

    private final LicensePaths paths;
    private final Clock clock;
    private final PublicKeyProvider publicKeyProvider;
    private final MachineIdSource machineIdSource;
    private final SettingsService settingsService;
    private final LicenseStatusCalculator calculator = new LicenseStatusCalculator();
    private final List<Consumer<LicenseStatus>> listeners = new CopyOnWriteArrayList<>();

    private final boolean devMode;
    private final String forcedState;

    private volatile LicenseStatus current;
    private volatile String cachedMachineId;
    private volatile ScheduledExecutorService scheduler;

    public LicenseService(LicensePaths paths, Clock clock, PublicKeyProvider publicKeyProvider,
            MachineIdSource machineIdSource, SettingsService settingsService) {
        this(paths, clock, publicKeyProvider, machineIdSource, settingsService,
                Boolean.getBoolean(DEV_FLAG), System.getProperty(FORCE_PROPERTY));
    }

    /** Test-friendly constructor: dev mode and the forced state are explicit. */
    public LicenseService(LicensePaths paths, Clock clock, PublicKeyProvider publicKeyProvider,
            MachineIdSource machineIdSource, SettingsService settingsService, boolean devMode, String forcedState) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.publicKeyProvider = Objects.requireNonNull(publicKeyProvider, "publicKeyProvider");
        this.machineIdSource = Objects.requireNonNull(machineIdSource, "machineIdSource");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.devMode = devMode;
        this.forcedState = forcedState;
        if (devMode) {
            log.warn("License DEV mode is active ({}={}); forced state: {}",
                    DEV_FLAG, true, forcedState == null ? "VALID" : forcedState);
        }
    }

    @Override
    public LicenseStatus current() {
        LicenseStatus status = current;
        if (status == null) {
            status = evaluate();
        }
        return status;
    }

    @Override
    public void requireWritable() {
        LicenseStatus status = current();
        if (status.state() != LicenseStatus.State.VALID) {
            throw new BusinessException("license.error.readonly");
        }
    }

    public boolean devMode() {
        return devMode;
    }

    /** This machine's code, or empty when it cannot be read. */
    public Optional<String> machineId() {
        return Optional.ofNullable(localMachineId());
    }

    public void addListener(Consumer<LicenseStatus> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<LicenseStatus> listener) {
        listeners.remove(listener);
    }

    /** Re-reads the license and updates the cached status; returns the new status. */
    public LicenseStatus evaluate() {
        LicenseStatus status = devMode ? devStatus() : productionStatus();
        LicenseStatus previous = current;
        current = status;
        boolean changed = previous == null
                || previous.state() != status.state()
                || !Objects.equals(previous.reasonKey(), status.reasonKey())
                || !Objects.equals(previous.daysLeft(), status.daysLeft());
        if (changed) {
            log.info("License state {}{}", status.state(),
                    status.reasonKey() == null ? "" : " (" + status.reasonKey() + ")");
        }
        for (Consumer<LicenseStatus> listener : listeners) {
            try {
                listener.accept(status);
            } catch (RuntimeException e) {
                log.warn("License listener failed: {}", e.getMessage());
            }
        }
        return status;
    }

    /**
     * Imports a license file. Only a VALID license is accepted; a rejected import
     * never changes the current license file. On success the previous file is kept
     * as {@code license.lic.bak} and the new one is moved into place atomically.
     */
    public LicenseStatus importLicense(Path source) {
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("license.error.import.malformed");
        }

        Optional<PublicKey> key = publicKeyProvider.publicKey();
        LicenseCheck check = LicenseVerifier.verify(text, key, localMachineId(), LocalDate.now(clock));
        switch (check.result()) {
            case VALID -> {
                // accepted
            }
            case EXPIRED -> throw new BusinessException("license.error.import.expired");
            case WRONG_MACHINE -> throw new BusinessException("license.error.import.wrong_machine");
            case BAD_SIGNATURE -> throw new BusinessException("license.error.import.bad_signature");
            case NO_PUBLIC_KEY -> throw new BusinessException("license.error.import.no_key");
            case NO_MACHINE_ID -> throw new BusinessException("license.error.import.no_machine_id");
            case MALFORMED, UNSUPPORTED_VERSION -> throw new BusinessException("license.error.import.malformed");
        }

        try {
            Path licenseFile = paths.licenseFile();
            Path directory = licenseFile.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            if (Files.isRegularFile(licenseFile)) {
                Files.copy(licenseFile, paths.backupFile(), StandardCopyOption.REPLACE_EXISTING);
            }
            Path temp = Files.createTempFile(directory, "license", ".tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, licenseFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, licenseFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("license.error.import.write");
        }
        log.info("License imported from a file ({} bytes)", text.length());
        return evaluate();
    }

    /** Starts the 30-minute daemon re-check (with a short first run). */
    public synchronized void startPeriodicChecks() {
        if (scheduler != null) {
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "license-recheck");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                evaluate();
            } catch (RuntimeException e) {
                log.warn("License re-check failed: {}", e.getMessage());
            }
        }, FIRST_RECHECK_DELAY_SECONDS, TimeUnit.MINUTES.toSeconds(RECHECK_PERIOD_MINUTES), TimeUnit.SECONDS);
        scheduler = executor;
    }

    /** Stops the daemon re-check (Spring shutdown / tests). */
    public synchronized void stopPeriodicChecks() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    // ------------------------------------------------------------------

    private LicenseStatus productionStatus() {
        String text = null;
        boolean present = Files.isRegularFile(paths.licenseFile());
        if (present) {
            try {
                text = Files.readString(paths.licenseFile(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("License file could not be read: {}", e.getMessage());
                text = null;
            }
        }
        LicenseCheck check;
        if (!present) {
            check = null;
        } else {
            check = LicenseVerifier.verify(text, publicKeyProvider.publicKey(), localMachineId(),
                    LocalDate.now(clock));
        }
        ClockGuard.Result clockResult = checkClock();
        persistLastSeen();
        return calculator.calculate(check, clockResult, LocalDate.now(clock));
    }

    private ClockGuard.Result checkClock() {
        Instant now = clock.instant();
        Instant lastSeen = maxLastSeen();
        return ClockGuard.check(lastSeen, now);
    }

    private void persistLastSeen() {
        Instant now = clock.instant();
        Instant next = ClockGuard.nextLastSeen(maxLastSeen(), now);
        try {
            settingsService.saveLicenseLastSeen(next);
        } catch (RuntimeException e) {
            log.warn("Could not persist license.last_seen: {}", e.getMessage());
        }
        try {
            Path stateFile = paths.stateFile();
            if (stateFile.getParent() != null) {
                Files.createDirectories(stateFile.getParent());
            }
            Files.writeString(stateFile, next.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.warn("Could not persist license state file: {}", e.getMessage());
        }
    }

    private Instant maxLastSeen() {
        Instant fromSettings = null;
        try {
            fromSettings = settingsService.loadLicenseLastSeen();
        } catch (RuntimeException e) {
            log.warn("Could not read license.last_seen: {}", e.getMessage());
        }
        Instant fromFile = readStateInstant();
        return ClockGuard.nextLastSeen(fromSettings, fromFile);
    }

    private Instant readStateInstant() {
        try {
            if (!Files.isRegularFile(paths.stateFile())) {
                return null;
            }
            String value = Files.readString(paths.stateFile(), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : Instant.parse(value);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private String localMachineId() {
        String cached = cachedMachineId;
        if (cached != null) {
            return cached;
        }
        try {
            String id = MachineId.compute(machineIdSource);
            cachedMachineId = id;
            return id;
        } catch (MachineIdException e) {
            log.warn("Machine id unavailable: {}", e.getMessage());
            return null;
        }
    }

    private LicenseStatus devStatus() {
        LocalDate today = LocalDate.now(clock);
        // The forced state may be set at runtime by the dev snapshot tool, so read
        // the system property first and fall back to the constructor value (tests).
        String live = System.getProperty(FORCE_PROPERTY);
        String selected = (live == null || live.isBlank()) ? forcedState : live;
        String force = selected == null ? "VALID" : selected.trim().toUpperCase(Locale.ROOT);
        return switch (force) {
            case "BLOCKED" -> new LicenseStatus(LicenseStatus.State.BLOCKED, false,
                    LicenseStatusCalculator.REASON_MISSING, "DEV", null, null, localMachineId(), "DEV");
            case "READ_ONLY" -> new LicenseStatus(LicenseStatus.State.READ_ONLY, false,
                    LicenseStatusCalculator.REASON_EXPIRED, "DEV", today.plusDays(30), 30,
                    localMachineId(), "DEV");
            case "EXPIRING" -> new LicenseStatus(LicenseStatus.State.VALID, true, null,
                    "DEV", today.plusDays(10), 10, localMachineId(), "DEV");
            default -> new LicenseStatus(LicenseStatus.State.VALID, false, null,
                    "DEV", today.plusDays(365), 365, localMachineId(), "DEV");
        };
    }
}
