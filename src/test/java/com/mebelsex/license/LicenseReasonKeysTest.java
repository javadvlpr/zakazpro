package com.mebelsex.license;

import com.mebelsex.common.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards that every status reason the calculator can produce has an Uzbek text. */
class LicenseReasonKeysTest {

    private static final List<String> REASON_KEYS = List.of(
            LicenseStatusCalculator.REASON_EXPIRED,
            LicenseStatusCalculator.REASON_CLOCK,
            LicenseStatusCalculator.REASON_WRONG_MACHINE,
            LicenseStatusCalculator.REASON_MISSING,
            LicenseStatusCalculator.REASON_BAD_SIGNATURE,
            LicenseStatusCalculator.REASON_MALFORMED,
            LicenseStatusCalculator.REASON_NO_KEY,
            LicenseStatusCalculator.REASON_NO_MACHINE_ID);

    @Test
    void everyCalculatorReasonKeyHasAText() {
        for (String key : REASON_KEYS) {
            assertTrue(Messages.contains(key), "missing message key: " + key);
        }
    }
}
