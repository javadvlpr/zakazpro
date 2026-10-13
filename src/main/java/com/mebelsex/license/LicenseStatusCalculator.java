package com.mebelsex.license;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Maps a {@link LicenseCheck} plus the clock guard result to the single
 * {@link LicenseStatus} the app acts on. Pure: no I/O, no clock reads.
 *
 * <p>Message keys are produced here; the actual translations are added later.
 */
public final class LicenseStatusCalculator {

    /** A license expiring within this many days still works but warns. */
    public static final int EXPIRING_SOON_DAYS = 14;

    public static final String REASON_EXPIRED = "license.reason.expired";
    public static final String REASON_CLOCK = "license.reason.clock";
    public static final String REASON_WRONG_MACHINE = "license.reason.wrong_machine";
    public static final String REASON_MISSING = "license.reason.missing";
    public static final String REASON_BAD_SIGNATURE = "license.reason.bad_signature";
    public static final String REASON_MALFORMED = "license.reason.malformed";
    public static final String REASON_NO_KEY = "license.reason.no_key";
    public static final String REASON_NO_MACHINE_ID = "license.reason.no_machine_id";

    public LicenseStatus calculate(LicenseCheck check, ClockGuard.Result clock, LocalDate today) {
        if (check == null) {
            return new LicenseStatus(LicenseStatus.State.BLOCKED, false, REASON_MISSING,
                    null, null, null, null, null);
        }

        LicensePayload payload = check.payload();
        Integer daysLeft = resolveDaysLeft(check, today);
        boolean expiringSoon = daysLeft != null && daysLeft >= 0 && daysLeft <= EXPIRING_SOON_DAYS;

        String customer = payload == null ? null : payload.customer();
        LocalDate expiresAt = payload == null ? null : payload.expiresAt();
        String machineId = payload == null ? null : payload.machineId();
        String licenseId = payload == null ? null : payload.licenseId();

        switch (check.result()) {
            case VALID:
                if (clock == ClockGuard.Result.ROLLED_BACK) {
                    return new LicenseStatus(LicenseStatus.State.READ_ONLY, false, REASON_CLOCK,
                            customer, expiresAt, daysLeft, machineId, licenseId);
                }
                return new LicenseStatus(LicenseStatus.State.VALID, expiringSoon, null,
                        customer, expiresAt, daysLeft, machineId, licenseId);
            case EXPIRED:
                return new LicenseStatus(LicenseStatus.State.READ_ONLY, false, REASON_EXPIRED,
                        customer, expiresAt, daysLeft, machineId, licenseId);
            case WRONG_MACHINE:
                return blocked(REASON_WRONG_MACHINE, payload, daysLeft);
            case BAD_SIGNATURE:
                return blocked(REASON_BAD_SIGNATURE, payload, daysLeft);
            case NO_PUBLIC_KEY:
                return blocked(REASON_NO_KEY, payload, daysLeft);
            case NO_MACHINE_ID:
                return blocked(REASON_NO_MACHINE_ID, payload, daysLeft);
            case UNSUPPORTED_VERSION:
            case MALFORMED:
            default:
                return blocked(REASON_MALFORMED, payload, daysLeft);
        }
    }

    private static LicenseStatus blocked(String reasonKey, LicensePayload payload, Integer daysLeft) {
        return new LicenseStatus(LicenseStatus.State.BLOCKED, false, reasonKey,
                payload == null ? null : payload.customer(),
                payload == null ? null : payload.expiresAt(),
                daysLeft,
                payload == null ? null : payload.machineId(),
                payload == null ? null : payload.licenseId());
    }

    private static Integer resolveDaysLeft(LicenseCheck check, LocalDate today) {
        if (check.daysLeft() != null) {
            return check.daysLeft();
        }
        LicensePayload payload = check.payload();
        if (payload != null && payload.expiresAt() != null && today != null) {
            return (int) ChronoUnit.DAYS.between(today, payload.expiresAt());
        }
        return null;
    }
}
