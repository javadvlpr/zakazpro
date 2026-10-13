package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for {@link SignedMoneyInputFormatter}. */
class SignedMoneyInputFormatterTest {

    private String text = "";
    private int caret = 0;

    private SignedMoneyInputFormatterTest type(String s) {
        MoneyInputFormatter.Result r = SignedMoneyInputFormatter.apply(text, caret, caret, caret, s);
        assertNotNull(r, "typing '" + s + "' was rejected");
        text = r.text();
        caret = r.caret();
        return this;
    }

    private SignedMoneyInputFormatterTest backspace() {
        MoneyInputFormatter.Result r = SignedMoneyInputFormatter.apply(text, caret, Math.max(0, caret - 1), caret, "");
        assertNotNull(r);
        text = r.text();
        caret = r.caret();
        return this;
    }

    private SignedMoneyInputFormatterTest expect(String expectedText, int expectedCaret) {
        assertEquals(expectedText, text);
        assertEquals(expectedCaret, caret);
        return this;
    }

    @Test
    void minusAloneIsAValidIntermediateState() {
        type("-").expect("-", 1);
        assertNull(SignedMoneyParser.parse("-"));
    }

    @Test
    void minusThenDigitsGroupsLikeMoney() {
        type("-").type("2").type("5").type("0").type("0").type("0").expect("-25 000", 7);
        assertEquals(new BigDecimal("-25000"), SignedMoneyParser.parse(text));
    }

    @Test
    void typingMinusInFrontMakesTheValueNegative() {
        type("2").type("5").type("0").type("0").type("0").expect("25 000", 6);
        this.text = "25 000";
        this.caret = 6;
        type("-").expect("-25 000", 7);
    }

    @Test
    void deletingTheLeadingMinusMakesItPositive() {
        this.text = "-25 000";
        this.caret = 1;
        backspace().expect("25 000", 0);
    }

    @Test
    void backspaceFromTheEndOfANegativeValue() {
        this.text = "-25 000";
        this.caret = 7;
        backspace().expect("-2 500", 6);
    }

    @Test
    void removingTheLastDigitKeepsJustTheMinus() {
        this.text = "-2";
        this.caret = 2;
        backspace().expect("-", 1);
        backspace().expect("", 0);
    }

    @Test
    void thirteenthDigitIsRejected() {
        this.text = "-999 999 999 999";
        this.caret = 16;
        assertNull(SignedMoneyInputFormatter.apply(text, caret, caret, caret, "9"));
    }

    @Test
    void lettersAreRejected() {
        this.text = "-25 000";
        this.caret = 7;
        assertNull(SignedMoneyInputFormatter.apply(text, caret, caret, caret, "a"));
        assertTrue(SignedMoneyInputFormatter.apply(text, caret, caret, caret, "-").text().startsWith("-"));
    }
}
