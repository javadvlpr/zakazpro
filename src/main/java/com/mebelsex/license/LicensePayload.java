package com.mebelsex.license;

import java.time.LocalDate;
import java.util.List;

/**
 * The signed content of a license file.
 *
 * @param formatVersion always {@link LicenseFormat#FORMAT_VERSION} for now
 * @param licenseId     unique id (UUID string) of this issued license
 * @param customer      workshop/customer name, non-blank
 * @param machineId     canonical machine code this license is bound to
 * @param issuedAt      issue date
 * @param expiresAt     expiry date, or null for a license that never expires
 * @param features      reserved feature list, never null (may be empty)
 */
public record LicensePayload(
        int formatVersion,
        String licenseId,
        String customer,
        String machineId,
        LocalDate issuedAt,
        LocalDate expiresAt,
        List<String> features) {

    public LicensePayload {
        if (licenseId == null || licenseId.isBlank()) {
            throw new IllegalArgumentException("licenseId bo'sh bo'lmasin");
        }
        if (customer == null || customer.isBlank()) {
            throw new IllegalArgumentException("customer bo'sh bo'lmasin");
        }
        if (machineId == null || machineId.isBlank()) {
            throw new IllegalArgumentException("machineId bo'sh bo'lmasin");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issuedAt bo'sh bo'lmasin");
        }
        features = features == null ? List.of() : List.copyOf(features);
    }
}
