package com.mebelsex.license;

import com.mebelsex.common.BusinessException;

/**
 * Test-only {@link LicenseGate} whose state can be switched. Defaults to VALID so
 * every existing test keeps writing business data unchanged.
 */
public class ToggleableLicenseGate implements LicenseGate {

    private volatile LicenseStatus.State state = LicenseStatus.State.VALID;
    private volatile String reasonKey;

    public void setState(LicenseStatus.State state, String reasonKey) {
        this.state = state == null ? LicenseStatus.State.VALID : state;
        this.reasonKey = reasonKey;
    }

    public void reset() {
        setState(LicenseStatus.State.VALID, null);
    }

    @Override
    public LicenseStatus current() {
        return new LicenseStatus(state, false, reasonKey, null, null, null, null, null);
    }

    @Override
    public void requireWritable() {
        if (state != LicenseStatus.State.VALID) {
            throw new BusinessException("license.error.readonly");
        }
    }
}
