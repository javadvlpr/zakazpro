package com.mebelsex.license;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip and tolerance tests for {@link LicenseFormat}. */
class LicenseFormatTest {

    private static final LocalDate ISSUED = LocalDate.of(2026, 1, 2);
    private static final String MACHINE = MachineIdTest.FIXED_CODE;

    private static KeyPair keys;

    @BeforeAll
    static void generateKeys() {
        keys = LicenseKeys.generate();
    }

    private static LicensePayload payload(String customer, LocalDate expires, List<String> features) {
        return new LicensePayload(1, "11111111-2222-3333-4444-555555555555",
                customer, MACHINE, ISSUED, expires, features);
    }

    @Test
    void roundTripKeepsEveryField() {
        LicensePayload original = payload("Mebel Sex MChJ", LocalDate.of(2027, 3, 4), List.of("orders", "reports"));
        String text = LicenseFormat.encode(original, keys.getPrivate());

        assertTrue(text.startsWith(LicenseFormat.HEADER));
        LicenseFormat.Decoded decoded = LicenseFormat.decode(text);

        assertEquals(original, decoded.payload());
        assertTrue(decoded.signature().length > 0);
        assertTrue(decoded.payloadBytes().length > 0);
    }

    @Test
    void licenseWithoutExpiryRoundTrips() {
        LicensePayload original = payload("Muddatsiz mijoz", null, List.of());
        LicenseFormat.Decoded decoded = LicenseFormat.decode(LicenseFormat.encode(original, keys.getPrivate()));
        assertNull(decoded.payload().expiresAt());
        assertTrue(decoded.payload().features().isEmpty());
    }

    @Test
    void unicodeCustomerRoundTrips() {
        String customer = "O'zbekiston Mebel — Янги Марказ, \"Katta\" filial";
        LicenseFormat.Decoded decoded = LicenseFormat.decode(
                LicenseFormat.encode(payload(customer, null, List.of()), keys.getPrivate()));
        assertEquals(customer, decoded.payload().customer());
    }

    @Test
    void customerWithEqualsAndNewlinesRoundTrips() {
        String customer = "Filial=A\nIkkinchi qator\r\nUchinchi";
        LicenseFormat.Decoded decoded = LicenseFormat.decode(
                LicenseFormat.encode(payload(customer, null, List.of()), keys.getPrivate()));
        assertEquals(customer, decoded.payload().customer());
    }

    @Test
    void veryLongCustomerRoundTrips() {
        String customer = "M".repeat(500);
        LicenseFormat.Decoded decoded = LicenseFormat.decode(
                LicenseFormat.encode(payload(customer, null, List.of()), keys.getPrivate()));
        assertEquals(customer, decoded.payload().customer());
    }

    @Test
    void crlfBomBlankLinesAndSpacesAreTolerated() {
        String text = LicenseFormat.encode(payload("CRLF mijoz", null, List.of()), keys.getPrivate());
        String noisy = "\uFEFF\n\n  " + text.replace("\n", "\r\n") + "\n   \n";
        LicenseFormat.Decoded decoded = LicenseFormat.decode(noisy);
        assertEquals("CRLF mijoz", decoded.payload().customer());
    }

    @Test
    void headerChangeIsRejected() {
        String text = LicenseFormat.encode(payload("A", null, List.of()), keys.getPrivate())
                .replace(LicenseFormat.HEADER, "MEBELSEX-LICENSE-9");
        LicenseFormatException ex = assertThrows(LicenseFormatException.class, () -> LicenseFormat.decode(text));
        assertEquals(LicenseFormatException.Reason.MALFORMED, ex.reason());
    }

    @Test
    void truncatedFileIsRejected() {
        String text = LicenseFormat.encode(payload("A", null, List.of()), keys.getPrivate());
        String twoLines = text.substring(0, text.indexOf('\n', text.indexOf('\n') + 1)) + "\n";
        assertThrows(LicenseFormatException.class, () -> LicenseFormat.decode(twoLines));
    }

    @Test
    void invalidBase64IsRejected() {
        String text = LicenseFormat.HEADER + "\n!!!!\n!!!!\n";
        LicenseFormatException ex = assertThrows(LicenseFormatException.class, () -> LicenseFormat.decode(text));
        assertEquals(LicenseFormatException.Reason.MALFORMED, ex.reason());
    }

    @Test
    void unsupportedVersionIsReportedSeparately() {
        String payloadText = "v=2\nid=x\ncustomer=" + b64url("C")
                + "\nmachine=" + MACHINE + "\nissued=2026-01-01\nexpires=\nfeatures=";
        String text = LicenseFormat.HEADER + "\n" + b64url(payloadText) + "\n" + b64url("imzo") ;

        LicenseFormatException ex = assertThrows(LicenseFormatException.class, () -> LicenseFormat.decode(text));
        assertEquals(LicenseFormatException.Reason.UNSUPPORTED_VERSION, ex.reason());
    }

    @Test
    void unknownPayloadKeysAreIgnored() {
        // A signed payload carrying an unknown key still decodes; the key stays covered by the signature.
        LicensePayload original = payload("Kengaytirilgan", null, List.of());
        byte[] bytes = LicenseFormat.payloadBytes(original);
        byte[] extended = (new String(bytes, StandardCharsets.UTF_8) + "\nextra=qiymat")
                .getBytes(StandardCharsets.UTF_8);
        String text = LicenseFormat.HEADER + "\n" + b64url(extended) + "\n"
                + b64url(LicenseFormat.sign(extended, keys.getPrivate()));

        LicenseFormat.Decoded decoded = LicenseFormat.decode(text);
        assertEquals("Kengaytirilgan", decoded.payload().customer());
        assertFalse(new String(decoded.payloadBytes(), StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void blankCustomerIsRejectedAsMalformed() {
        String payloadText = "v=1\nid=x\ncustomer=" + b64url("   ")
                + "\nmachine=" + MACHINE + "\nissued=2026-01-01\nexpires=\nfeatures=";
        String text = LicenseFormat.HEADER + "\n" + b64url(payloadText) + "\n" + b64url("imzo");
        assertThrows(LicenseFormatException.class, () -> LicenseFormat.decode(text));
    }

    private static String b64url(String value) {
        return b64url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
