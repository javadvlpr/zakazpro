package com.mebelsex.license;

import java.time.LocalDate;

/**
 * Outcome of verifying a license file.
 *
 * @param result   what happened, see {@link Result}
 * @param payload  the parsed payload when it could be decoded, otherwise null
 * @param daysLeft days until expiry, or null when the license never expires
 */
public record LicenseCheck(Result result, LicensePayload payload, Integer daysLeft) {

    public enum Result {
        /** Signature and machine ok, not past the end of the expiry day. */
        VALID,
        /** Valid signature and machine, but the expiry day has passed. */
        EXPIRED,
        /** File structure/encoding/fields are wrong. */
        MALFORMED,
        /** Ed25519 signature does not match. */
        BAD_SIGNATURE,
        /** License is bound to another machine. */
        WRONG_MACHINE,
        /** Newer license format than this app understands. */
        UNSUPPORTED_VERSION,
        /** No verification key configured (placeholder or missing). */
        NO_PUBLIC_KEY,
        /** This machine's id could not be determined. */
        NO_MACHINE_ID
    }

    /** Convenience: the customer name when a payload is present. */
    public String customer() {
        return payload == null ? null : payload.customer();
    }

    /** Convenience: the expiry date when a payload is present. */
    public LocalDate expiresAt() {
        return payload == null ? null : payload.expiresAt();
    }

    /** Convenience: the license id when a payload is present. */
    public String licenseId() {
        return payload == null ? null : payload.licenseId();
    }
}
