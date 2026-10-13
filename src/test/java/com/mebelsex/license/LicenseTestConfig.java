package com.mebelsex.license;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration: installs the {@link ToggleableLicenseGate} as the primary
 * {@link LicenseGate} for every Spring test context. It defaults to VALID, so the
 * production write guard never blocks existing tests; the license tests switch it
 * to READ_ONLY/BLOCKED when they need to. Lives only on the test classpath.
 */
@Configuration
public class LicenseTestConfig {

    @Bean
    @Primary
    public ToggleableLicenseGate toggleableLicenseGate() {
        return new ToggleableLicenseGate();
    }
}
