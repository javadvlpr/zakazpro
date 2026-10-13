package com.mebelsex.license;

/**
 * Thrown when a license file or key file cannot be parsed.
 * {@link Reason#UNSUPPORTED_VERSION} lets the verifier distinguish a newer
 * license format from plain corruption.
 */
public class LicenseFormatException extends RuntimeException {

    public enum Reason {
        MALFORMED,
        UNSUPPORTED_VERSION
    }

    private final Reason reason;

    public LicenseFormatException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LicenseFormatException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
