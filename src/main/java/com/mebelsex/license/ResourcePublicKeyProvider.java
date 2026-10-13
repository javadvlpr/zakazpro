package com.mebelsex.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Optional;

/**
 * Reads the license public key from a classpath resource
 * (default {@code /license/license-public.key}).
 *
 * <p>Never throws: a missing resource, the shipped placeholder text
 * ({@link #PLACEHOLDER}) or unreadable key all report "no key", so a build
 * without a real key stays usable and simply treats licenses as unlicensed.
 */
public final class ResourcePublicKeyProvider implements PublicKeyProvider {

    /** Placeholder text shipped until the owner runs keygen. */
    public static final String PLACEHOLDER = "PLACEHOLDER-RUN-KEYGEN";

    public static final String DEFAULT_RESOURCE = "/license/license-public.key";

    private final String resourcePath;

    public ResourcePublicKeyProvider() {
        this(DEFAULT_RESOURCE);
    }

    public ResourcePublicKeyProvider(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public Optional<PublicKey> publicKey() {
        try (InputStream in = ResourcePublicKeyProvider.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return Optional.empty();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.contains(PLACEHOLDER)) {
                return Optional.empty();
            }
            if (text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(LicenseKeys.parsePublicKey(text));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
