package com.mebelsex.ui.components;

import java.math.BigDecimal;

/**
 * Pure parser for fractional quantities (e.g. 15.5 m²).
 * Accepts digits with at most one '.' or ',' separator and at most 3 decimals.
 * Returns null for empty or malformed input.
 */
public final class QuantityParser {

    private QuantityParser() {
    }

    public static BigDecimal parse(String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim().replace(" ", "").replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        if (!text.matches("\\d+(\\.\\d{1,3})?")) {
            return null;
        }
        return new BigDecimal(text);
    }

    public static String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
