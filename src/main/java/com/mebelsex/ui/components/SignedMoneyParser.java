package com.mebelsex.ui.components;

import java.math.BigDecimal;

/**
 * Pure parser/formatter for signed money (e.g. the employee opening balance).
 * Accepts an optional leading minus, spaces and digits. Returns null for empty or malformed input.
 */
public final class SignedMoneyParser {

    private SignedMoneyParser() {
    }

    public static BigDecimal parse(String input) {
        if (input == null) {
            return null;
        }
        String clean = input.trim().replace(" ", "");
        boolean negative = clean.startsWith("-");
        String digits = clean.replace("-", "");
        if (digits.isEmpty() || !digits.matches("\\d{1,12}")) {
            return null;
        }
        BigDecimal value = new BigDecimal(digits);
        return negative ? value.negate() : value;
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        boolean negative = amount.signum() < 0;
        String digits = amount.abs().toPlainString();
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.insert(0, digits.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                grouped.insert(0, " ");
            }
        }
        return (negative ? "-" : "") + grouped;
    }
}
