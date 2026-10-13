package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for {@link QuantityInputFormatter} (digits, one '.'/',' separator, at most 3 decimals). */
class QuantityInputFormatterTest {

    @Test
    void acceptsEmptyAndDigits() {
        assertTrue(QuantityInputFormatter.accepts(""));
        assertTrue(QuantityInputFormatter.accepts("0"));
        assertTrue(QuantityInputFormatter.accepts("15"));
        assertTrue(QuantityInputFormatter.accepts("12345678901234567890"));
    }

    @Test
    void acceptsOneSeparatorWithUpToThreeDecimals() {
        assertTrue(QuantityInputFormatter.accepts("15.5"));
        assertTrue(QuantityInputFormatter.accepts("15,5"));
        assertTrue(QuantityInputFormatter.accepts("0.123"));
        assertTrue(QuantityInputFormatter.accepts("15."));
        assertTrue(QuantityInputFormatter.accepts("."));
    }

    @Test
    void rejectsMoreThanThreeDecimalsOrTwoSeparators() {
        assertFalse(QuantityInputFormatter.accepts("15.5555"));
        assertFalse(QuantityInputFormatter.accepts("1.2.3"));
        assertFalse(QuantityInputFormatter.accepts("1,2,3"));
        assertFalse(QuantityInputFormatter.accepts("1.2,"));
    }

    @Test
    void rejectsLettersAndMinus() {
        assertFalse(QuantityInputFormatter.accepts("a"));
        assertFalse(QuantityInputFormatter.accepts("15a"));
        assertFalse(QuantityInputFormatter.accepts("-15"));
        assertFalse(QuantityInputFormatter.accepts("15 000"));
    }

    @Test
    void applyReturnsTheRawTextAndCaretWhenAccepted() {
        MoneyInputFormatter.Result r = QuantityInputFormatter.apply("", 0, 0, 0, "15.5");
        assertNotNull(r);
        assertEquals("15.5", r.text());
        assertEquals(4, r.caret());
    }

    @Test
    void applyInsertsInTheMiddle() {
        MoneyInputFormatter.Result r = QuantityInputFormatter.apply("15", 1, 1, 1, ".");
        assertNotNull(r);
        assertEquals("1.5", r.text());
        assertEquals(2, r.caret());
    }

    @Test
    void applyRejectsInvalidInsertions() {
        assertNull(QuantityInputFormatter.apply("", 0, 0, 0, "a"));
        assertNull(QuantityInputFormatter.apply("15", 2, 2, 2, "a"));
        assertNull(QuantityInputFormatter.apply("15.555", 6, 6, 6, "5"));
        assertNull(QuantityInputFormatter.apply("1.5", 3, 3, 3, "."));
    }

    @Test
    void editedTextStillParsesWithQuantityParser() {
        MoneyInputFormatter.Result r = QuantityInputFormatter.apply("", 0, 0, 0, "1,5");
        assertEquals(new BigDecimal("1.5"), QuantityParser.parse(r.text()));
    }
}
