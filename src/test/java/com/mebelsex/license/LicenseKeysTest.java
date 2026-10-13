package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Key generation and key-file text round trips for {@link LicenseKeys}. */
class LicenseKeysTest {

    @Test
    void generatesDistinctEd25519Pairs() {
        KeyPair first = LicenseKeys.generate();
        KeyPair second = LicenseKeys.generate();
        assertEquals("EdDSA", first.getPrivate().getAlgorithm());
        assertNotEquals(first.getPublic().getEncoded().length, 0);
        assertTrue(!java.util.Arrays.equals(first.getPublic().getEncoded(), second.getPublic().getEncoded()));
    }

    @Test
    void privateKeyTextRoundTrips() {
        KeyPair keys = LicenseKeys.generate();
        String text = LicenseKeys.encodePrivateKeyText(keys.getPrivate());
        assertTrue(text.startsWith(LicenseKeys.PRIVATE_HEADER));
        assertArrayEquals(keys.getPrivate().getEncoded(),
                LicenseKeys.parsePrivateKey(text).getEncoded());
    }

    @Test
    void publicKeyTextRoundTrips() {
        KeyPair keys = LicenseKeys.generate();
        String text = LicenseKeys.encodePublicKeyText(keys.getPublic());
        assertTrue(text.startsWith(LicenseKeys.PUBLIC_HEADER));
        assertArrayEquals(keys.getPublic().getEncoded(),
                LicenseKeys.parsePublicKey(text).getEncoded());
    }

    @Test
    void parsersTolerateWhitespaceNoise() {
        KeyPair keys = LicenseKeys.generate();
        String privateText = LicenseKeys.encodePrivateKeyText(keys.getPrivate());
        String publicText = LicenseKeys.encodePublicKeyText(keys.getPublic());

        String noisyPrivate = "\uFEFF\r\n  " + privateText.replace("\n", "\r\n") + "\n   \n";
        String noisyPublic = wrap(publicText);

        assertArrayEquals(keys.getPrivate().getEncoded(),
                LicenseKeys.parsePrivateKey(noisyPrivate).getEncoded());
        assertArrayEquals(keys.getPublic().getEncoded(),
                LicenseKeys.parsePublicKey(noisyPublic).getEncoded());
    }

    @Test
    void wrongHeaderIsRejected() {
        KeyPair keys = LicenseKeys.generate();
        String text = LicenseKeys.encodePublicKeyText(keys.getPublic())
                .replace(LicenseKeys.PUBLIC_HEADER, "MEBELSEX-WRONG-KEY");
        assertThrows(LicenseFormatException.class, () -> LicenseKeys.parsePublicKey(text));
    }

    @Test
    void emptyKeyTextIsRejected() {
        assertThrows(LicenseFormatException.class, () -> LicenseKeys.parsePublicKey(null));
        assertThrows(LicenseFormatException.class, () -> LicenseKeys.parsePublicKey("   "));
    }

    /** Splits the base64 body over several lines with extra spaces, as a wrapped file would. */
    private static String wrap(String keyText) {
        String[] lines = keyText.trim().split("\n");
        String body = lines[1];
        String half = body.substring(0, body.length() / 2);
        return lines[0] + "\r\n  " + half + "\r\n  " + body.substring(body.length() / 2) + "\n";
    }
}
