package com.mebelsex.ui.components;

import java.math.BigDecimal;

public class MoneyParser {

    public static BigDecimal parse(String moneyString) {
        if (moneyString == null || moneyString.trim().isEmpty()) {
            return null;
        }

        // Remove spaces, currency symbol ("so'm", "som"), commas, and non-digit
        // characters except for a minus sign
        // Wait, negative not allowed
        String cleanString = moneyString.replaceAll("[^0-9]", "");

        if (cleanString.isEmpty()) {
            return null;
        }

        // Limit the length to avoid overflow or out of bounds (max 12 digits requested)
        if (cleanString.length() > 12) {
            cleanString = cleanString.substring(0, 12);
        }

        return new BigDecimal(cleanString);
    }

    public static String format(BigDecimal amount) {
        if (amount == null)
            return "";
        String str = amount.toPlainString();
        StringBuilder formatted = new StringBuilder();
        int count = 0;
        for (int i = str.length() - 1; i >= 0; i--) {
            formatted.insert(0, str.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                formatted.insert(0, " ");
            }
        }
        return formatted.toString();
    }
}
