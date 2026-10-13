package com.mebelsex.license;

import com.mebelsex.settings.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;

/**
 * Wires the license beans. Registers {@link LicenseService} as a bean (not a
 * {@code @Service}) so tests can substitute their own {@link LicenseGate}
 * without fighting component scanning.
 */
@Configuration
public class LicenseConfig {

    @Bean
    public Clock licenseClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public MachineIdSource licenseMachineIdSource() {
        return new WindowsMachineGuidSource();
    }

    @Bean
    public PublicKeyProvider licensePublicKeyProvider() {
        return new ResourcePublicKeyProvider();
    }

    @Bean
    public LicensePaths licensePaths() {
        return LicensePaths.forDatabaseDirectory(defaultDatabaseDirectory());
    }

    @Bean
    public LicenseService licenseService(LicensePaths licensePaths, Clock licenseClock,
            PublicKeyProvider licensePublicKeyProvider, MachineIdSource licenseMachineIdSource,
            SettingsService settingsService) {
        return new LicenseService(licensePaths, licenseClock, licensePublicKeyProvider,
                licenseMachineIdSource, settingsService);
    }

    /** The directory that holds the live database; dev/test DBs keep their own license. */
    static Path defaultDatabaseDirectory() {
        String dbPath = System.getProperty("mebelsex.db.path");
        if (dbPath != null && !dbPath.isBlank()) {
            Path resolved = Paths.get(dbPath).toAbsolutePath();
            return resolved.getParent() == null ? Paths.get(".").toAbsolutePath() : resolved.getParent();
        }
        return Paths.get("target", "license").toAbsolutePath();
    }
}
