package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuantityParserTest {

    @Test
    void emptyAndNullReturnNull() {
        assertNull(QuantityParser.parse(null));
        assertNull(QuantityParser.parse(""));
        assertNull(QuantityParser.parse("   "));
    }

    @Test
    void wholeNumberParses() {
        assertEquals(0, QuantityParser.parse("0").compareTo(BigDecimal.ZERO));
        assertEquals(0, QuantityParser.parse("12").compareTo(new BigDecimal("12")));
    }

    @Test
    void commaAndDotSeparatorsAreAccepted() {
        assertEquals(0, QuantityParser.parse("15,5").compareTo(new BigDecimal("15.5")));
        assertEquals(0, QuantityParser.parse("15.5").compareTo(new BigDecimal("15.5")));
        assertEquals(0, QuantityParser.parse("15.500").compareTo(new BigDecimal("15.5")));
    }

    @Test
    void moreThanThreeDecimalsRejected() {
        assertNull(QuantityParser.parse("15.5000"));
        assertNull(QuantityParser.parse("15,1234"));
    }

    @Test
    void lettersAndMalformedRejected() {
        assertNull(QuantityParser.parse("abc"));
        assertNull(QuantityParser.parse("12a"));
        assertNull(QuantityParser.parse(",5"));
        assertNull(QuantityParser.parse("1.2.3"));
        assertNull(QuantityParser.parse("-3"));
    }
}
