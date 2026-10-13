package com.mebelsex.release;

import com.mebelsex.common.AppInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure tests for the jpackage version normalization. */
class InstallerVersionTest {

    @Test
    void keepsAThreePartNumericVersion() {
        assertEquals("1.0.0", InstallerVersion.normalizeInstallerVersion("1.0.0"));
        assertEquals("10.20.30", InstallerVersion.normalizeInstallerVersion("10.20.30"));
    }

    @Test
    void dropsThePrereleaseAndBuildSuffix() {
        assertEquals("1.0.0", InstallerVersion.normalizeInstallerVersion("1.0.0-SNAPSHOT"));
        assertEquals("2.1.3", InstallerVersion.normalizeInstallerVersion("2.1.3-rc1"));
        assertEquals("2.1.3", InstallerVersion.normalizeInstallerVersion("2.1.3+build.7"));
        assertEquals("1.0.0", InstallerVersion.normalizeInstallerVersion("1.0.0-SNAPSHOT+build"));
    }

    @Test
    void padsMissingPartsWithZero() {
        assertEquals("2.5.0", InstallerVersion.normalizeInstallerVersion("2.5"));
        assertEquals("3.0.0", InstallerVersion.normalizeInstallerVersion("3"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("1.2.0", InstallerVersion.normalizeInstallerVersion("  1.2  "));
    }

    @Test
    void theAppVersionNormalizesToItself() {
        assertEquals("1.0.0", InstallerVersion.normalizeInstallerVersion(AppInfo.VERSION));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion(null));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion(""));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("   "));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("-SNAPSHOT"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("abc"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("v1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1.2.x"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1..2"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1.02.0"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1,0,0"));
    }

    @Test
    void rejectsAnOutOfRangePartAndAZeroMajor() {
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("1.2.65536"));
        assertThrows(IllegalArgumentException.class, () -> InstallerVersion.normalizeInstallerVersion("0.1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> InstallerVersion.normalizeInstallerVersion("1.2.99999999999999999999"));
    }
}
