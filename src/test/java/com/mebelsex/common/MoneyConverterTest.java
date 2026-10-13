package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyConverterTest {

    private final MoneyConverter converter = new MoneyConverter();

    @Test
    void convertToDatabaseColumn_roundTrip() {
        BigDecimal amount = new BigDecimal("25000000");
        Long dbValue = converter.convertToDatabaseColumn(amount);
        assertEquals(25_000_000L, dbValue);

        BigDecimal back = converter.convertToEntityAttribute(dbValue);
        assertEquals(new BigDecimal("25000000"), back);
    }

    @Test
    void convertToDatabaseColumn_roundsHalfUp() {
        BigDecimal amount = new BigDecimal("100.5");
        Long dbValue = converter.convertToDatabaseColumn(amount);
        assertEquals(101L, dbValue, "0.5 should round up");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute_null() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToDatabaseColumn_zero() {
        assertEquals(0L, converter.convertToDatabaseColumn(BigDecimal.ZERO));
    }

    @Test
    void convertToDatabaseColumn_negative() {
        BigDecimal amount = new BigDecimal("-500000");
        Long dbValue = converter.convertToDatabaseColumn(amount);
        assertEquals(-500_000L, dbValue);
    }
}
