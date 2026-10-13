package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignedMoneyParserTest {

    @Test
    void parsesPositiveNegativeAndSpacedInput() {
        assertEquals(new BigDecimal("5000000"), SignedMoneyParser.parse("5000000"));
        assertEquals(new BigDecimal("5000000"), SignedMoneyParser.parse("5 000 000"));
        assertEquals(new BigDecimal("-5000000"), SignedMoneyParser.parse("-5000000"));
        assertEquals(new BigDecimal("-5000000"), SignedMoneyParser.parse(" -5 000 000 "));
        assertEquals(BigDecimal.ZERO.negate(), SignedMoneyParser.parse("-0"));
    }

    @Test
    void returnsNullForEmptyOrMalformed() {
        assertNull(SignedMoneyParser.parse(null));
        assertNull(SignedMoneyParser.parse(""));
        assertNull(SignedMoneyParser.parse("   "));
        assertNull(SignedMoneyParser.parse("-"));
        assertNull(SignedMoneyParser.parse("abc"));
        assertNull(SignedMoneyParser.parse("1.5"));
        assertNull(SignedMoneyParser.parse("1234567890123"));
    }

    @Test
    void formatsWithGroupingAndKeepsSign() {
        assertEquals("5 000 000", SignedMoneyParser.format(new BigDecimal("5000000")));
        assertEquals("-5 000 000", SignedMoneyParser.format(new BigDecimal("-5000000")));
        assertEquals("0", SignedMoneyParser.format(BigDecimal.ZERO));
        assertEquals("-250", SignedMoneyParser.format(new BigDecimal("-250")));
        assertEquals("", SignedMoneyParser.format(null));
    }
}
