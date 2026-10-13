package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The (verification result, clock, days left) -> app state matrix. */
class LicenseStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private final LicenseStatusCalculator calculator = new LicenseStatusCalculator();

    private static LicensePayload payload(LocalDate expires) {
        return new LicensePayload(1, "id-1", "Mijoz", MachineIdTest.FIXED_CODE,
                LocalDate.of(2026, 1, 1), expires, List.of());
    }

    private static LicenseCheck check(LicenseCheck.Result result, Integer daysLeft) {
        return new LicenseCheck(result, payload(LocalDate.of(2026, 12, 31)), daysLeft);
    }

    @Test
    void validAndClockOkIsValid() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.VALID, 60),
                ClockGuard.Result.OK, TODAY);
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertNull(status.reasonKey());
        assertFalse(status.expiringSoon());
        assertEquals("Mijoz", status.customer());
        assertEquals(MachineIdTest.FIXED_CODE, status.machineId());
        assertEquals("id-1", status.licenseId());
    }

    @Test
    void fourteenDaysLeftIsExpiringSoon() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.VALID, 14),
                ClockGuard.Result.OK, TODAY);
        assertEquals(LicenseStatus.State.VALID, status.state());
        assertTrue(status.expiringSoon());
    }

    @Test
    void fifteenDaysLeftIsNotExpiringSoon() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.VALID, 15),
                ClockGuard.Result.OK, TODAY);
        assertTrue(status.state() == LicenseStatus.State.VALID && !status.expiringSoon());
    }

    @Test
    void zeroDaysLeftIsExpiringSoon() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.VALID, 0),
                ClockGuard.Result.OK, TODAY);
        assertTrue(status.expiringSoon());
    }

    @Test
    void noExpiryIsNeverExpiringSoon() {
        LicenseCheck noExpiry = new LicenseCheck(LicenseCheck.Result.VALID, payload(null), null);
        LicenseStatus status = calculator.calculate(noExpiry, ClockGuard.Result.OK, TODAY);
        assertFalse(status.expiringSoon());
        assertNull(status.daysLeft());
        assertNull(status.expiresAt());
    }

    @Test
    void validWithRolledBackClockIsReadOnly() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.VALID, 60),
                ClockGuard.Result.ROLLED_BACK, TODAY);
        assertEquals(LicenseStatus.State.READ_ONLY, status.state());
        assertEquals(LicenseStatusCalculator.REASON_CLOCK, status.reasonKey());
    }

    @Test
    void expiredIsReadOnly() {
        LicenseStatus status = calculator.calculate(check(LicenseCheck.Result.EXPIRED, -3),
                ClockGuard.Result.OK, TODAY);
        assertEquals(LicenseStatus.State.READ_ONLY, status.state());
        assertEquals(LicenseStatusCalculator.REASON_EXPIRED, status.reasonKey());
        assertFalse(status.expiringSoon());
    }

    @Test
    void blockingResultsMapToTheirReasons() {
        assertEquals(LicenseStatusCalculator.REASON_BAD_SIGNATURE, blockedReason(LicenseCheck.Result.BAD_SIGNATURE));
        assertEquals(LicenseStatusCalculator.REASON_WRONG_MACHINE, blockedReason(LicenseCheck.Result.WRONG_MACHINE));
        assertEquals(LicenseStatusCalculator.REASON_MALFORMED, blockedReason(LicenseCheck.Result.MALFORMED));
        assertEquals(LicenseStatusCalculator.REASON_MALFORMED, blockedReason(LicenseCheck.Result.UNSUPPORTED_VERSION));
        assertEquals(LicenseStatusCalculator.REASON_NO_KEY, blockedReason(LicenseCheck.Result.NO_PUBLIC_KEY));
        assertEquals(LicenseStatusCalculator.REASON_NO_MACHINE_ID, blockedReason(LicenseCheck.Result.NO_MACHINE_ID));
    }

    @Test
    void missingLicenseIsBlocked() {
        LicenseStatus status = calculator.calculate(null, ClockGuard.Result.OK, TODAY);
        assertEquals(LicenseStatus.State.BLOCKED, status.state());
        assertEquals(LicenseStatusCalculator.REASON_MISSING, status.reasonKey());
    }

    @Test
    void daysLeftIsComputedFromTheExpiryWhenAbsent() {
        LicenseCheck noDays = new LicenseCheck(LicenseCheck.Result.VALID,
                payload(TODAY.plusDays(10)), null);
        LicenseStatus status = calculator.calculate(noDays, ClockGuard.Result.OK, TODAY);
        assertEquals(10, status.daysLeft());
        assertTrue(status.expiringSoon());
    }

    private String blockedReason(LicenseCheck.Result result) {
        LicenseStatus status = calculator.calculate(check(result, null), ClockGuard.Result.OK, TODAY);
        assertEquals(LicenseStatus.State.BLOCKED, status.state());
        return status.reasonKey();
    }
}
