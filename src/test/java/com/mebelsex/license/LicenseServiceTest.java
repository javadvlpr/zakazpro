package com.mebelsex.license;

import com.mebelsex.common.BusinessException;
import com.mebelsex.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Status evaluation, persistence and import rules for {@link LicenseService}. */
class LicenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final String MACHINE = MachineIdTest.FIXED_CODE;
    private static final String OTHER_MACHINE = MachineIdTest.FIXED_CODE_2;

    @TempDir
    Path temp;

    private LicensePaths paths;
    private FakeSettings settings;
    private KeyPair keys;
    private Clock clock;

    @BeforeEach
    void setUp() {
        paths = LicensePaths.forDatabaseDirectory(temp);
        settings = new FakeSettings();
        keys = LicenseKeys.generate();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    // ---- status matrix -------------------------------------------------

    @Test
    void validLicenseIsValid() {
        writeLicense(validText(keys, MACHINE, null));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertEquals("Test mijoz", status.customer());
        assertEquals(MACHINE, status.machineId());
    }

    @Test
    void fourteenDaysLeftIsExpiringSoon() {
        writeLicense(validText(keys, MACHINE, TODAY.plusDays(14)));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertTrue(status.expiringSoon());
        assertEquals(14, status.daysLeft());
    }

    @Test
    void fifteenDaysLeftIsNotExpiringSoon() {
        writeLicense(validText(keys, MACHINE, TODAY.plusDays(15)));
        LicenseStatus status = service().evaluate();
        assertTrue(status.state() == LicenseStatus.State.VALID && !status.expiringSoon());
    }

    @Test
    void expiredLicenseIsReadOnly() {
        writeLicense(validText(keys, MACHINE, TODAY.minusDays(1)));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.READ_ONLY, status.state());
        assertEquals(LicenseStatusCalculator.REASON_EXPIRED, status.reasonKey());
    }

    @Test
    void clockRollbackFromSettingsIsReadOnly() {
        settings.saveLicenseLastSeen(NOW.plusSeconds(48 * 3600));
        writeLicense(validText(keys, MACHINE, null));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.READ_ONLY, status.state());
        assertEquals(LicenseStatusCalculator.REASON_CLOCK, status.reasonKey());
    }

    @Test
    void clockRollbackFromStateFileIsReadOnly() throws Exception {
        Files.writeString(paths.stateFile(), NOW.plusSeconds(48 * 3600).toString(), StandardCharsets.UTF_8);
        writeLicense(validText(keys, MACHINE, null));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.READ_ONLY, status.state());
        assertEquals(LicenseStatusCalculator.REASON_CLOCK, status.reasonKey());
    }

    @Test
    void missingLicenseIsBlocked() {
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatus.State.BLOCKED, status.state());
        assertEquals(LicenseStatusCalculator.REASON_MISSING, status.reasonKey());
    }

    @Test
    void missingPublicKeyIsBlockedNoKey() {
        writeLicense(validText(keys, MACHINE, null));
        // Production default when no key is configured: a provider that reports "no key".
        LicenseService service = new LicenseService(paths, clock, () -> Optional.empty(),
                () -> MachineIdTest.FIXED_GUID, settings, false, null);
        LicenseStatus status = service.evaluate();
        assertEquals(LicenseStatus.State.BLOCKED, status.state());
        assertEquals(LicenseStatusCalculator.REASON_NO_KEY, status.reasonKey());
    }

    @Test
    void noMachineIdIsBlocked() {
        writeLicense(validText(keys, MACHINE, null));
        LicenseService service = new LicenseService(paths, clock, keyProvider(), () -> {
            throw new MachineIdException("yo'q");
        }, settings, false, null);
        LicenseStatus status = service.evaluate();
        assertEquals(LicenseStatusCalculator.REASON_NO_MACHINE_ID, status.reasonKey());
    }

    @Test
    void wrongMachineIsBlocked() {
        writeLicense(validText(keys, OTHER_MACHINE, null));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatusCalculator.REASON_WRONG_MACHINE, status.reasonKey());
    }

    @Test
    void badSignatureIsBlocked() {
        KeyPair other = LicenseKeys.generate();
        writeLicense(validText(other, MACHINE, null));
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatusCalculator.REASON_BAD_SIGNATURE, status.reasonKey());
    }

    @Test
    void garbageIsMalformed() {
        writeLicense("bu litsenziya emas");
        LicenseStatus status = service().evaluate();
        assertEquals(LicenseStatusCalculator.REASON_MALFORMED, status.reasonKey());
    }

    // ---- last-seen persistence ----------------------------------------

    @Test
    void evaluatePersistsLastSeenAndNeverMovesItBackwards() throws Exception {
        writeLicense(validText(keys, MACHINE, null));
        service().evaluate();
        assertEquals(NOW, settings.loadLicenseLastSeen());
        assertEquals(NOW, Instant.parse(Files.readString(paths.stateFile()).trim()));

        // A clock one hour back is inside the tolerance and must not lower last_seen.
        Clock back = Clock.fixed(NOW.minusSeconds(3600), ZoneOffset.UTC);
        LicenseService second = new LicenseService(paths, back, keyProvider(), machineSource(), settings,
                false, null);
        LicenseStatus status = second.evaluate();
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertEquals(NOW, settings.loadLicenseLastSeen());
        assertEquals(NOW, Instant.parse(Files.readString(paths.stateFile()).trim()));
    }

    // ---- import --------------------------------------------------------

    @Test
    void importValidReplacesTheFileAndKeepsTheBackup() throws Exception {
        writeLicense(validText(keys, MACHINE, null));
        String oldText = Files.readString(paths.licenseFile());
        Path imported = importFile(validText(keys, MACHINE, TODAY.plusDays(30)));

        LicenseStatus status = service().importLicense(imported);

        assertEquals(LicenseStatus.State.VALID, status.state());
        assertEquals(oldText, Files.readString(paths.backupFile()));
        assertNotNull(Files.readString(paths.licenseFile()));
    }

    @Test
    void importExpiredLeavesTheCurrentLicenseUntouched() throws Exception {
        String current = validText(keys, MACHINE, null);
        writeLicense(current);
        Path imported = importFile(validText(keys, MACHINE, TODAY.minusDays(1)));
        BusinessException e = assertThrows(BusinessException.class, () -> service().importLicense(imported));
        assertEquals("license.error.import.expired", e.getMessageKey());
        assertEquals(current, Files.readString(paths.licenseFile()));
    }

    @Test
    void importWrongMachineLeavesTheCurrentLicenseUntouched() throws Exception {
        String current = validText(keys, MACHINE, null);
        writeLicense(current);
        Path imported = importFile(validText(keys, OTHER_MACHINE, null));
        BusinessException e = assertThrows(BusinessException.class, () -> service().importLicense(imported));
        assertEquals("license.error.import.wrong_machine", e.getMessageKey());
        assertEquals(current, Files.readString(paths.licenseFile()));
    }

    @Test
    void importBadSignatureLeavesTheCurrentLicenseUntouched() throws Exception {
        String current = validText(keys, MACHINE, null);
        writeLicense(current);
        KeyPair other = LicenseKeys.generate();
        Path imported = importFile(validText(other, MACHINE, null));
        BusinessException e = assertThrows(BusinessException.class, () -> service().importLicense(imported));
        assertEquals("license.error.import.bad_signature", e.getMessageKey());
        assertEquals(current, Files.readString(paths.licenseFile()));
    }

    @Test
    void importGarbageLeavesTheCurrentLicenseUntouched() throws Exception {
        String current = validText(keys, MACHINE, null);
        writeLicense(current);
        Path imported = importFile("shunchaki matn");
        BusinessException e = assertThrows(BusinessException.class, () -> service().importLicense(imported));
        assertEquals("license.error.import.malformed", e.getMessageKey());
        assertEquals(current, Files.readString(paths.licenseFile()));
    }

    @Test
    void importWithoutExistingLicenseStillWorks() throws Exception {
        Path imported = importFile(validText(keys, MACHINE, null));
        LicenseStatus status = service().importLicense(imported);
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertFalse(Files.exists(paths.backupFile()));
    }

    // ---- dev flag ------------------------------------------------------

    @Test
    void devFlagForcesStates() {
        assertEquals(LicenseStatus.State.VALID, service(true, null).evaluate().state());
        assertEquals(LicenseStatus.State.READ_ONLY, service(true, "READ_ONLY").evaluate().state());
        assertEquals(LicenseStatus.State.BLOCKED, service(true, "BLOCKED").evaluate().state());
        LicenseStatus expiring = service(true, "EXPIRING").evaluate();
        assertEquals(LicenseStatus.State.VALID, expiring.state());
        assertTrue(expiring.expiringSoon());
        assertTrue(service(true, null).devMode());
    }

    @Test
    void requireWritableThrowsWhenNotValid() {
        writeLicense(validText(keys, MACHINE, TODAY.minusDays(1)));
        LicenseService service = service();
        service.evaluate();
        BusinessException e = assertThrows(BusinessException.class, service::requireWritable);
        assertEquals("license.error.readonly", e.getMessageKey());
    }

    // ---- helpers -------------------------------------------------------

    private LicenseService service() {
        return service(false, null);
    }

    private LicenseService service(boolean dev, String force) {
        return new LicenseService(paths, clock, keyProvider(), machineSource(), settings, dev, force);
    }

    private PublicKeyProvider keyProvider() {
        return () -> Optional.of(keys.getPublic());
    }

    private MachineIdSource machineSource() {
        return () -> MachineIdTest.FIXED_GUID;
    }

    private void writeLicense(String text) {
        try {
            Files.createDirectories(paths.licenseFile().getParent());
            Files.writeString(paths.licenseFile(), text, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path importFile(String text) {
        try {
            Path file = temp.resolve("import-" + UUID.randomUUID() + ".lic");
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String validText(KeyPair pair, String machine, LocalDate expires) {
        LicensePayload payload = new LicensePayload(1, UUID.randomUUID().toString(), "Test mijoz",
                machine, TODAY, expires, List.of());
        return LicenseFormat.encode(payload, pair.getPrivate());
    }

    /** Settings stand-in; only the license last-seen accessors are used. */
    private static final class FakeSettings extends SettingsService {
        private Instant lastSeen;

        FakeSettings() {
            super(null);
        }

        @Override
        public Instant loadLicenseLastSeen() {
            return lastSeen;
        }

        @Override
        public void saveLicenseLastSeen(Instant instant) {
            lastSeen = instant;
        }
    }
}
