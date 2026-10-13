package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeTest {

    @Test
    void parsesKnownNamesCaseInsensitively() {
        assertEquals(Theme.LIGHT, Theme.parse("LIGHT"));
        assertEquals(Theme.DARK, Theme.parse("dark"));
        assertEquals(Theme.DARK, Theme.parse("  Dark "));
    }

    @Test
    void missingOrUnknownValueFallsBackToLight() {
        assertEquals(Theme.LIGHT, Theme.parse(null));
        assertEquals(Theme.LIGHT, Theme.parse(""));
        assertEquals(Theme.LIGHT, Theme.parse("   "));
        assertEquals(Theme.LIGHT, Theme.parse("BANANA"));
    }

    @Test
    void toggledSwitchesToTheOtherTheme() {
        assertEquals(Theme.DARK, Theme.LIGHT.toggled());
        assertEquals(Theme.LIGHT, Theme.DARK.toggled());
    }
}
