package com.mebelsex.license;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verification order, signature, machine binding and expiry boundary. */
class LicenseVerifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final String MACHINE = MachineIdTest.FIXED_CODE;
    private static final String OTHER_MACHINE = MachineIdTest.FIXED_CODE_2;

    private static KeyPair keys;

    @BeforeAll
    static void generateKeys() {
        keys = LicenseKeys.generate();
    }

    private static LicensePayload payload(String machine, LocalDate expires) {
        return new LicensePayload(1, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "Mebel Sex MChJ", machine, LocalDate.of(2026, 1, 1), expires, List.of());
    }

    private static LicenseCheck verify(String text, String machine, LocalDate today) {
        return LicenseVerifier.verify(text, Optional.of(keys.getPublic()), machine, today);
    }

    @Test
    void validLicensePasses() {
        String text = LicenseFormat.encode(payload(MACHINE, LocalDate.of(2027, 1, 1)), keys.getPrivate());
        LicenseCheck check = verify(text, MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.VALID, check.result());
        assertNotNull(check.payload());
        assertEquals("Mebel Sex MChJ", check.customer());
        assertEquals((int) ChronoUnit.DAYS.between(TODAY, LocalDate.of(2027, 1, 1)), check.daysLeft().intValue());
    }

    @Test
    void licenseExpiringTodayIsStillValid() {
        String text = LicenseFormat.encode(payload(MACHINE, TODAY), keys.getPrivate());
        LicenseCheck check = verify(text, MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.VALID, check.result());
        assertEquals(0, check.daysLeft());
    }

    @Test
    void licenseExpiringTomorrowHasOneDayLeft() {
        String text = LicenseFormat.encode(payload(MACHINE, TODAY.plusDays(1)), keys.getPrivate());
        LicenseCheck check = verify(text, MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.VALID, check.result());
        assertEquals(1, check.daysLeft());
    }

    @Test
    void licenseThatExpiredYesterdayIsExpired() {
        String text = LicenseFormat.encode(payload(MACHINE, TODAY.minusDays(1)), keys.getPrivate());
        LicenseCheck check = verify(text, MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.EXPIRED, check.result());
        assertEquals(-1, check.daysLeft());
    }

    @Test
    void licenseWithoutExpiryIsAlwaysValid() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        LicenseCheck check = verify(text, MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.VALID, check.result());
        assertNull(check.daysLeft());
    }

    @Test
    void tamperedExpiryFailsTheSignature() {
        String text = LicenseFormat.encode(payload(MACHINE, LocalDate.of(2027, 1, 1)), keys.getPrivate());
        LicenseFormat.Decoded decoded = LicenseFormat.decode(text);
        byte[] tampered = LicenseFormat.payloadBytes(payload(MACHINE, LocalDate.of(2040, 1, 1)));
        String tamperedText = LicenseFormat.HEADER + "\n" + b64url(tampered) + "\n"
                + b64url(decoded.signature()) + "\n";
        assertEquals(LicenseCheck.Result.BAD_SIGNATURE, verify(tamperedText, MACHINE, TODAY).result());
    }

    @Test
    void tamperedMachineFailsTheSignature() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        LicenseFormat.Decoded decoded = LicenseFormat.decode(text);
        byte[] tampered = LicenseFormat.payloadBytes(payload(OTHER_MACHINE, null));
        String tamperedText = LicenseFormat.HEADER + "\n" + b64url(tampered) + "\n"
                + b64url(decoded.signature()) + "\n";
        assertEquals(LicenseCheck.Result.BAD_SIGNATURE, verify(tamperedText, MACHINE, TODAY).result());
    }

    @Test
    void anotherKeyDoesNotVerify() {
        KeyPair other = LicenseKeys.generate();
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        LicenseCheck check = LicenseVerifier.verify(text, Optional.of(other.getPublic()), MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.BAD_SIGNATURE, check.result());
    }

    @Test
    void wrongMachineIsReported() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        LicenseCheck check = verify(text, OTHER_MACHINE, TODAY);
        assertEquals(LicenseCheck.Result.WRONG_MACHINE, check.result());
        assertNotNull(check.payload());
    }

    @Test
    void licenseMachineInAnotherFormatStillMatches() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        String messyMachine = MACHINE.replace("-", " ").toLowerCase();
        assertEquals(LicenseCheck.Result.VALID, verify(text, messyMachine, TODAY).result());
    }

    @Test
    void truncatedFileIsMalformed() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        String truncated = text.substring(0, text.indexOf('\n', text.indexOf('\n') + 1) + 1);
        assertEquals(LicenseCheck.Result.MALFORMED, verify(truncated, MACHINE, TODAY).result());
    }

    @Test
    void unsupportedVersionIsReported() {
        String payloadText = "v=9\nid=x\ncustomer=" + b64url("C") + "\nmachine=" + MACHINE
                + "\nissued=2026-01-01\nexpires=\nfeatures=";
        String text = LicenseFormat.HEADER + "\n" + b64url(payloadText) + "\n" + b64url("imzo") + "\n";
        assertEquals(LicenseCheck.Result.UNSUPPORTED_VERSION, verify(text, MACHINE, TODAY).result());
    }

    @Test
    void noPublicKeyIsReported() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        assertEquals(LicenseCheck.Result.NO_PUBLIC_KEY,
                LicenseVerifier.verify(text, Optional.empty(), MACHINE, TODAY).result());
        assertEquals(LicenseCheck.Result.NO_PUBLIC_KEY,
                LicenseVerifier.verify(text, new ResourcePublicKeyProvider("/license/does-not-exist.key").publicKey(),
                        MACHINE, TODAY).result());
    }

    @Test
    void noMachineIdIsReported() {
        String text = LicenseFormat.encode(payload(MACHINE, null), keys.getPrivate());
        assertEquals(LicenseCheck.Result.NO_MACHINE_ID, verify(text, "  ", TODAY).result());
        assertEquals(LicenseCheck.Result.NO_MACHINE_ID, verify(text, null, TODAY).result());
    }

    @Test
    void expiredLicenseStillNeedsTheRightMachine() {
        String text = LicenseFormat.encode(payload(MACHINE, TODAY.minusDays(2)), keys.getPrivate());
        assertEquals(LicenseCheck.Result.WRONG_MACHINE, verify(text, OTHER_MACHINE, TODAY).result());
    }

    private static String b64url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
