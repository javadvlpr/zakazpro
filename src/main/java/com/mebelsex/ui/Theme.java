package com.mebelsex.ui;

/**
 * The two visual themes. LIGHT is the default ("Kunduzgi"); DARK is "Tungi".
 * The choice is stored in the settings table under the key {@code ui.theme}.
 */
public enum Theme {
    LIGHT,
    DARK;

    /** Parses a stored value; null/blank/unknown values fall back to {@link #LIGHT}. */
    public static Theme parse(String value) {
        if (value == null) {
            return LIGHT;
        }
        String trimmed = value.trim();
        for (Theme theme : values()) {
            if (theme.name().equalsIgnoreCase(trimmed)) {
                return theme;
            }
        }
        return LIGHT;
    }

    /** The other theme. */
    public Theme toggled() {
        return this == DARK ? LIGHT : DARK;
    }
}
