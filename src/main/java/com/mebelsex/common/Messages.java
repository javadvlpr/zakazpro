package com.mebelsex.common;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The single loading point for the Uzbek message bundle. Every other class uses
 * {@link #bundle()}, {@link #get(String)} or {@link #format(String, Object...)} so the
 * inconsistent {@code i18n.messages} + Locale and {@code i18n.messages_uz} lookups
 * disappear.
 */
public final class Messages {

    private static final Locale UZ = Locale.forLanguageTag("uz");
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("i18n.messages_uz", UZ);

    private Messages() {
    }

    /** Shared bundle (used for FXML loading and direct getString calls). */
    public static ResourceBundle bundle() {
        return BUNDLE;
    }

    public static boolean contains(String key) {
        return BUNDLE.containsKey(key);
    }

    public static String get(String key) {
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String format(String key, Object... args) {
        return new MessageFormat(BUNDLE.getString(key), UZ).format(args);
    }
}
