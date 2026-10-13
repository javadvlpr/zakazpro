package com.mebelsex.license;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Verifies a license file against the public key and this machine, in the order
 * parse -> key -> machine id -> signature -> machine -> expiry. Pure and
 * deterministic: it never reads the clock, files or the registry itself.
 */
public final class LicenseVerifier {

    private LicenseVerifier() {
    }

    /**
     * @param fileText  the license file text
     * @param key       the verification key, empty when none is configured
     * @param machineId this machine's canonical code, may be blank/unknown
     * @param today     the day to test the expiry against
     */
    public static LicenseCheck verify(String fileText, Optional<PublicKey> key, String machineId, LocalDate today) {
        LicenseFormat.Decoded decoded;
        try {
            decoded = LicenseFormat.decode(fileText);
        } catch (LicenseFormatException e) {
            LicenseCheck.Result result = e.reason() == LicenseFormatException.Reason.UNSUPPORTED_VERSION
                    ? LicenseCheck.Result.UNSUPPORTED_VERSION
                    : LicenseCheck.Result.MALFORMED;
            return new LicenseCheck(result, null, null);
        }

        LicensePayload payload = decoded.payload();

        if (key == null || key.isEmpty()) {
            return new LicenseCheck(LicenseCheck.Result.NO_PUBLIC_KEY, payload, null);
        }
        if (machineId == null || machineId.isBlank()) {
            return new LicenseCheck(LicenseCheck.Result.NO_MACHINE_ID, payload, null);
        }

        if (!signatureMatches(decoded.payloadBytes(), decoded.signature(), key.get())) {
            return new LicenseCheck(LicenseCheck.Result.BAD_SIGNATURE, payload, null);
        }

        if (!machineMatches(payload.machineId(), machineId)) {
            return new LicenseCheck(LicenseCheck.Result.WRONG_MACHINE, payload, null);
        }

        Integer daysLeft = null;
        if (payload.expiresAt() != null) {
            daysLeft = (int) ChronoUnit.DAYS.between(today, payload.expiresAt());
            if (payload.expiresAt().isBefore(today)) {
                return new LicenseCheck(LicenseCheck.Result.EXPIRED, payload, daysLeft);
            }
        }
        return new LicenseCheck(LicenseCheck.Result.VALID, payload, daysLeft);
    }

    private static boolean signatureMatches(byte[] payloadBytes, byte[] signatureBytes, PublicKey key) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(payloadBytes);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static boolean machineMatches(String licenseMachine, String localMachine) {
        try {
            return MachineId.normalize(licenseMachine).equals(MachineId.normalize(localMachine));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
