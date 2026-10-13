package com.mebelsex.license;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 key generation and the plain-text key file format used by the
 * offline license tool.
 *
 * <pre>
 * license-private.key:  MEBELSEX-PRIVATE-KEY-1  / base64(PKCS#8)
 * license-public.key:   MEBELSEX-PUBLIC-KEY-1   / base64(X.509)
 * </pre>
 *
 * Parsers are whitespace-tolerant (extra blank lines, wrapped base64, CRLF, BOM).
 */
public final class LicenseKeys {

    public static final String PRIVATE_HEADER = "MEBELSEX-PRIVATE-KEY-1";
    public static final String PUBLIC_HEADER = "MEBELSEX-PUBLIC-KEY-1";

    private LicenseKeys() {
    }

    /** Generates a fresh Ed25519 key pair. */
    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 kalit jufti yaratilmadi", e);
        }
    }

    /** Serializes a private key to the key file text (header + base64 PKCS#8). */
    public static String encodePrivateKeyText(PrivateKey privateKey) {
        return PRIVATE_HEADER + "\n" + Base64.getEncoder().encodeToString(privateKey.getEncoded()) + "\n";
    }

    /** Serializes a public key to the key file text (header + base64 X.509). */
    public static String encodePublicKeyText(PublicKey publicKey) {
        return PUBLIC_HEADER + "\n" + Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n";
    }

    /** Parses a private key file text. */
    public static PrivateKey parsePrivateKey(String text) {
        byte[] der = decodeKeyText(text, PRIVATE_HEADER);
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new LicenseFormatException(LicenseFormatException.Reason.MALFORMED,
                    "Private kalit o'qilmadi", e);
        }
    }

    /** Parses a public key file text. */
    public static PublicKey parsePublicKey(String text) {
        byte[] der = decodeKeyText(text, PUBLIC_HEADER);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new LicenseFormatException(LicenseFormatException.Reason.MALFORMED,
                    "Public kalit o'qilmadi", e);
        }
    }

    private static byte[] decodeKeyText(String text, String header) {
        if (text == null) {
            throw malformed("Kalit fayli bo'sh");
        }
        String cleaned = text;
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
            cleaned = cleaned.substring(1);
        }
        cleaned = cleaned.replace("\r\n", "\n").replace('\r', '\n');

        StringBuilder base64 = new StringBuilder();
        boolean headerSeen = false;
        for (String raw : cleaned.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!headerSeen) {
                if (!header.equals(line)) {
                    throw malformed("Kalit sarlavhasi noto'g'ri");
                }
                headerSeen = true;
                continue;
            }
            base64.append(line);
        }
        if (!headerSeen || base64.length() == 0) {
            throw malformed("Kalit fayli to'liq emas");
        }
        try {
            return Base64.getDecoder().decode(base64.toString());
        } catch (IllegalArgumentException e) {
            throw new LicenseFormatException(LicenseFormatException.Reason.MALFORMED,
                    "Kalit base64 qismi noto'g'ri", e);
        }
    }

    private static LicenseFormatException malformed(String message) {
        return new LicenseFormatException(LicenseFormatException.Reason.MALFORMED, message);
    }
}
