package com.mebelsex.license;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The on-disk license file format and its payload encoding.
 *
 * <pre>
 * MEBELSEX-LICENSE-1
 * &lt;payload as base64url, no padding&gt;
 * &lt;Ed25519 signature as base64url, no padding&gt;
 * </pre>
 *
 * The payload is UTF-8 text with LF newlines: {@code v}, {@code id},
 * {@code customer} (base64url of the UTF-8 name), {@code machine},
 * {@code issued}, {@code expires} and {@code features}. The signature covers the
 * exact payload bytes, so any unknown key stays protected as well.
 */
public final class LicenseFormat {

    public static final String HEADER = "MEBELSEX-LICENSE-1";
    public static final int FORMAT_VERSION = 1;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private LicenseFormat() {
    }

    /** A decoded license: the parsed payload together with the raw signed parts. */
    public record Decoded(LicensePayload payload, byte[] payloadBytes, byte[] signature) {
    }

    /** Builds the full license file text and signs the payload with the private key. */
    public static String encode(LicensePayload payload, PrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey bo'sh bo'lmasin");
        }
        byte[] payloadBytes = payloadBytes(payload);
        byte[] signature = sign(payloadBytes, privateKey);
        return HEADER + "\n" + ENCODER.encodeToString(payloadBytes) + "\n"
                + ENCODER.encodeToString(signature) + "\n";
    }

    /**
     * Parses a license file. Tolerant of CRLF, a UTF-8 BOM and surrounding
     * blank lines/spaces; strict about the header, the line count, base64,
     * dates and the format version.
     *
     * @throws LicenseFormatException when the file is malformed or too new
     */
    public static Decoded decode(String fileText) {
        if (fileText == null) {
            throw malformed("Litsenziya matni bo'sh");
        }
        List<String> lines = new ArrayList<>();
        for (String raw : normalize(fileText).split("\n", -1)) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.size() != 3) {
            throw malformed("Litsenziya fayli 3 ta qatordan iborat bo'lishi kerak");
        }
        if (!HEADER.equals(lines.get(0))) {
            throw malformed("Litsenziya sarlavhasi noto'g'ri");
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = DECODER.decode(lines.get(1));
            signature = DECODER.decode(lines.get(2));
        } catch (IllegalArgumentException e) {
            throw new LicenseFormatException(LicenseFormatException.Reason.MALFORMED,
                    "Litsenziya base64 qismi noto'g'ri", e);
        }
        return new Decoded(parsePayload(payloadBytes), payloadBytes, signature);
    }

    /** The exact bytes that are signed: the UTF-8 payload text, LF-separated, no trailing LF. */
    static byte[] payloadBytes(LicensePayload payload) {
        if (payload.formatVersion() != FORMAT_VERSION) {
            throw new LicenseFormatException(LicenseFormatException.Reason.UNSUPPORTED_VERSION,
                    "Qo'llab-quvvatlanmaydigan litsenziya versiyasi: " + payload.formatVersion());
        }
        List<String> lines = new ArrayList<>();
        lines.add("v=" + payload.formatVersion());
        lines.add("id=" + payload.licenseId());
        lines.add("customer=" + ENCODER.encodeToString(payload.customer().getBytes(StandardCharsets.UTF_8)));
        lines.add("machine=" + payload.machineId());
        lines.add("issued=" + payload.issuedAt());
        lines.add("expires=" + (payload.expiresAt() == null ? "" : payload.expiresAt().toString()));
        lines.add("features=" + String.join(",", payload.features()));
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] sign(byte[] payloadBytes, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Litsenziya imzolanmadi", e);
        }
    }

    private static LicensePayload parsePayload(byte[] payloadBytes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : new String(payloadBytes, StandardCharsets.UTF_8).split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                throw malformed("Litsenziya payload qatori noto'g'ri: " + trimmed);
            }
            values.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
        }

        int version = parseVersion(values.get("v"));
        if (version != FORMAT_VERSION) {
            throw new LicenseFormatException(LicenseFormatException.Reason.UNSUPPORTED_VERSION,
                    "Qo'llab-quvvatlanmaydigan litsenziya versiyasi: " + version);
        }

        String licenseId = required(values, "id");
        String customer = decodeCustomer(required(values, "customer"));
        String machineId = required(values, "machine");
        LocalDate issuedAt = parseDate(required(values, "issued"), "issued");
        String expiresRaw = values.get("expires");
        LocalDate expiresAt = (expiresRaw == null || expiresRaw.isBlank())
                ? null : parseDate(expiresRaw, "expires");

        List<String> features = new ArrayList<>();
        String featuresRaw = values.get("features");
        if (featuresRaw != null && !featuresRaw.isBlank()) {
            for (String feature : featuresRaw.split(",")) {
                String trimmed = feature.trim();
                if (!trimmed.isEmpty()) {
                    features.add(trimmed);
                }
            }
        }

        try {
            return new LicensePayload(version, licenseId, customer, machineId, issuedAt, expiresAt, features);
        } catch (IllegalArgumentException e) {
            throw new LicenseFormatException(LicenseFormatException.Reason.MALFORMED, e.getMessage(), e);
        }
    }

    private static int parseVersion(String value) {
        if (value == null || value.isBlank()) {
            throw malformed("Litsenziya versiyasi yo'q");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw malformed("Litsenziya versiyasi noto'g'ri: " + value);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw malformed("Litsenziyada '" + key + "' maydoni yo'q");
        }
        return value.trim();
    }

    private static String decodeCustomer(String encoded) {
        try {
            return new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw malformed("Mijoz nomi noto'g'ri kodlangan");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw malformed("Sana noto'g'ri (" + field + "): " + value);
        }
    }

    private static String normalize(String fileText) {
        String text = fileText;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static LicenseFormatException malformed(String message) {
        return new LicenseFormatException(LicenseFormatException.Reason.MALFORMED, message);
    }
}
