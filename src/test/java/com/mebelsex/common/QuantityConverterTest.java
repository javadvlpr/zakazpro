package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QuantityConverterTest {

    private final QuantityConverter converter = new QuantityConverter();

    @Test
    void roundTrip() {
        BigDecimal qty = new BigDecimal("15.5");
        String dbValue = converter.convertToDatabaseColumn(qty);
        assertEquals("15.5", dbValue);

        BigDecimal back = converter.convertToEntityAttribute(dbValue);
        assertEquals(new BigDecimal("15.5"), back);
    }

    @Test
    void wholeNumber() {
        BigDecimal qty = new BigDecimal("10");
        String dbValue = converter.convertToDatabaseColumn(qty);
        assertEquals("10", dbValue);

        BigDecimal back = converter.convertToEntityAttribute(dbValue);
        assertEquals(new BigDecimal("10"), back);
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
    void convertToEntityAttribute_blank() {
        assertNull(converter.convertToEntityAttribute("  "));
    }
}
