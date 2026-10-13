package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FormattersTest {

    @Test
    void formatMoney_typical() {
        assertEquals("25 000 000 so'm", Formatters.formatMoney(new BigDecimal("25000000")));
    }

    @Test
    void formatMoney_small() {
        assertEquals("1 500 so'm", Formatters.formatMoney(new BigDecimal("1500")));
    }

    @Test
    void formatMoney_zero() {
        assertEquals("0 so'm", Formatters.formatMoney(BigDecimal.ZERO));
    }

    @Test
    void formatMoney_negative() {
        assertEquals("-500 000 so'm", Formatters.formatMoney(new BigDecimal("-500000")));
    }

    @Test
    void formatMoney_null() {
        assertEquals("\u2014", Formatters.formatMoney(null));
    }

    @Test
    void formatDate_typical() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        assertEquals("15.03.2026", Formatters.formatDate(date));
    }

    @Test
    void formatDate_null() {
        assertEquals("\u2014", Formatters.formatDate(null));
    }
}
