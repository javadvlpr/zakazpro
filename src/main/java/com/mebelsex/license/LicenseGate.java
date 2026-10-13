package com.mebelsex.license;

/**
 * The single gate the application asks about licensing. Read paths consult
 * {@link #current()}; every business write must call {@link #requireWritable()}.
 */
public interface LicenseGate {

    /** The latest known status; never null (evaluate first when in doubt). */
    LicenseStatus current();

    /**
     * Must be called before writing business data.
     *
     * @throws com.mebelsex.common.BusinessException with key {@code license.error.readonly}
     *         when the app is READ_ONLY or BLOCKED
     */
    void requireWritable();
}
