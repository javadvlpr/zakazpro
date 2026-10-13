package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class MoneyParserTest {

    @Test
    public void testParseEmptyAndNull() {
        assertNull(MoneyParser.parse(null));
        assertNull(MoneyParser.parse(""));
        assertNull(MoneyParser.parse("   "));
    }

    @Test
    public void testParseWithSpaces() {
        assertEquals(new BigDecimal("25000000"), MoneyParser.parse("25 000 000"));
        assertEquals(new BigDecimal("100"), MoneyParser.parse(" 1 0 0 "));
    }

    @Test
    public void testParseLeadingZeros() {
        assertEquals(new BigDecimal("500"), MoneyParser.parse("000500"));
    }

    @Test
    public void testParseWithCurrency() {
        assertEquals(new BigDecimal("25000000"), MoneyParser.parse("25 000 000 so'm"));
        assertEquals(new BigDecimal("5000"), MoneyParser.parse("5000som"));
        assertEquals(new BigDecimal("5000"), MoneyParser.parse("5000 uzs"));
    }

    @Test
    public void testMaxLength12() {
        assertEquals(new BigDecimal("123456789012"), MoneyParser.parse("123,456,789,012,345"));
        assertEquals(new BigDecimal("999999999999"), MoneyParser.parse("999 999 999 999 9"));
    }

    @Test
    public void testNegativeNotAllowedOrStripped() {
        // Requirements say negative not allowed. The parser strips the '-' char.
        assertEquals(new BigDecimal("5000"), MoneyParser.parse("-5000"));
    }

    @Test
    public void testFormat() {
        assertEquals("25 000 000", MoneyParser.format(new BigDecimal("25000000")));
        assertEquals("100", MoneyParser.format(new BigDecimal("100")));
        assertEquals("1 000", MoneyParser.format(new BigDecimal("1000")));
        assertEquals("", MoneyParser.format(null));
    }
}
