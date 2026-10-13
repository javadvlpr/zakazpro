package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The provider must never throw and must refuse the placeholder text. */
class ResourcePublicKeyProviderTest {

    @Test
    void placeholderTextReportsNoKey() {
        assertTrue(new ResourcePublicKeyProvider("/license/placeholder-public.key").publicKey().isEmpty());
    }

    @Test
    void missingResourceReportsNoKey() {
        assertTrue(new ResourcePublicKeyProvider("/license/does-not-exist.key").publicKey().isEmpty());
    }

    @Test
    void shippedResourceIsHandledWithoutThrowing() {
        // The shipped file is either the placeholder (no key) or the owner's real
        // public key (a key); both outcomes are fine, throwing is not.
        assertNotNull(new ResourcePublicKeyProvider().publicKey());
    }
}
