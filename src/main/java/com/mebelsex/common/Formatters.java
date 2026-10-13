package com.mebelsex.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility class for formatting money and dates per AGENTS.md UI rules.
 * <ul>
 * <li>Money: "25 000 000 so'm" (space grouped, with so'm suffix)</li>
 * <li>Date: dd.MM.yyyy</li>
 * </ul>
 * Handles null and negative values.
 */
public final class Formatters {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final DecimalFormat MONEY_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');
        MONEY_FMT = new DecimalFormat("#,##0", symbols);
    }

    private Formatters() {
        // utility class
    }

    /**
     * Formats a BigDecimal as money: "25 000 000 so'm".
     * Null returns "—". Negative amounts get a leading minus: "-500 000 so'm".
     */
    public static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "\u2014"; // em-dash
        }
        synchronized (MONEY_FMT) {
            return MONEY_FMT.format(amount) + " so'm";
        }
    }

    /**
     * Formats a LocalDate as dd.MM.yyyy.
     * Null returns "—".
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "\u2014"; // em-dash
        }
        return date.format(DATE_FMT);
    }
}
