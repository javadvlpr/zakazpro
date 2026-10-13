package com.mebelsex.license;

import java.time.LocalDate;

/**
 * The app-facing license state.
 *
 * @param state       what the app may do, see {@link State}
 * @param expiringSoon true when the license expires within 14 days (warning only)
 * @param reasonKey   i18n key explaining a non-VALID state, null when VALID
 * @param customer    licensed customer name, when known
 * @param expiresAt   expiry date, null when the license never expires
 * @param daysLeft    days until expiry, null when the license never expires
 * @param machineId   machine code the license is bound to, when known
 * @param licenseId   license id, when known
 */
public record LicenseStatus(
        State state,
        boolean expiringSoon,
        String reasonKey,
        String customer,
        LocalDate expiresAt,
        Integer daysLeft,
        String machineId,
        String licenseId) {

    public enum State {
        /** License valid; the app runs normally. */
        VALID,
        /** License expired or the clock moved back; the app is read-only. */
        READ_ONLY,
        /** No valid license; the app must not be used. */
        BLOCKED
    }
}
