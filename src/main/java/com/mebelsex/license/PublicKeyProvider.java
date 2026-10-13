package com.mebelsex.license;

import java.security.PublicKey;
import java.util.Optional;

/**
 * Supplies the application's license verification key.
 * Returns {@link Optional#empty()} when no usable key is configured.
 */
@FunctionalInterface
public interface PublicKeyProvider {

    Optional<PublicKey> publicKey();
}
