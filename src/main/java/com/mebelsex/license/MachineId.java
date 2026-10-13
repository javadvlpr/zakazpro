package com.mebelsex.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Derives a short, stable, human-typable machine code from a raw machine id.
 * The code is a hash of the raw id, so the raw MachineGuid never leaves the machine.
 */
public final class MachineId {

    /** Code prefix shown to the user. */
    public static final String PREFIX = "MX";

    /** Crockford-style alphabet without I, O, 0 and 1 to avoid look-alike mistakes. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 16;
    private static final int HASH_BYTES = 10;
    private static final String SALT = "mebelsex-machine-v1:";

    private MachineId() {
    }

    /**
     * Reads the raw id from the given source and computes the canonical machine code.
     *
     * @throws MachineIdException when the source is unavailable or its id is empty
     */
    public static String compute(MachineIdSource source) throws MachineIdException {
        if (source == null) {
            throw new MachineIdException("MachineIdSource berilmagan");
        }
        return fromRawId(source.readRawId());
    }

    /**
     * Computes the canonical machine code from a raw machine id.
     * SHA-256 over {@code "mebelsex-machine-v1:" + id.trim().toLowerCase(ROOT)},
     * first 10 bytes, base32-encoded with {@link #ALPHABET}.
     */
    public static String fromRawId(String rawId) throws MachineIdException {
        if (rawId == null || rawId.isBlank()) {
            throw new MachineIdException("MachineGuid bo'sh");
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest((SALT + normalized).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new MachineIdException("SHA-256 mavjud emas", e);
        }
        byte[] head = new byte[HASH_BYTES];
        System.arraycopy(digest, 0, head, 0, HASH_BYTES);
        return format(base32(head));
    }

    /** Encodes bytes as base32 text using {@link #ALPHABET}. */
    static String base32(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(ALPHABET.charAt((buffer >>> bits) & 0x1F));
            }
        }
        return sb.toString();
    }

    /** Formats 16 code characters as {@code MX-ABCD-EFGH-IJKL-MNOP}. */
    public static String format(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("Machine kodi 16 belgidan iborat bo'lishi kerak");
        }
        return PREFIX + "-" + code.substring(0, 4) + "-" + code.substring(4, 8)
                + "-" + code.substring(8, 12) + "-" + code.substring(12, 16);
    }

    /**
     * Accepts user input in any casing, with or without dashes/spaces, with or
     * without the {@code MX} prefix, and returns the canonical {@code MX-....} form.
     *
     * @throws IllegalArgumentException when the input cannot be a machine code
     */
    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Machine kodi bo'sh");
        }
        String compact = value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (compact.startsWith(PREFIX)) {
            compact = compact.substring(PREFIX.length());
        }
        if (compact.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("Machine kodi 16 belgidan iborat bo'lishi kerak: " + value);
        }
        for (int i = 0; i < compact.length(); i++) {
            if (ALPHABET.indexOf(compact.charAt(i)) < 0) {
                throw new IllegalArgumentException("Machine kodida noto'g'ri belgi: " + compact.charAt(i));
            }
        }
        return format(compact);
    }
}
