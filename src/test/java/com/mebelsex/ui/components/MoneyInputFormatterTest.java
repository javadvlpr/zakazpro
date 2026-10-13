package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link MoneyInputFormatter} (no JavaFX toolkit). They drive a tiny (text, caret)
 * model of a TextField so every case is deterministic.
 */
class MoneyInputFormatterTest {

    /** Minimal driver around MoneyInputFormatter.apply. */
    private static final class Model {
        String text = "";
        int caret = 0;

        Model type(String s) {
            MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, caret, caret, s);
            assertNotNull(r, "typing '" + s + "' at " + caret + " of '" + text + "' was rejected");
            text = r.text();
            caret = r.caret();
            return this;
        }

        Model reject(String s) {
            assertNull(MoneyInputFormatter.apply(text, caret, caret, caret, s),
                    "'" + s + "' should be rejected");
            return this;
        }

        Model at(int c) {
            caret = c;
            return this;
        }

        Model backspace() {
            MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, Math.max(0, caret - 1), caret, "");
            assertNotNull(r);
            text = r.text();
            caret = r.caret();
            return this;
        }

        Model delete() {
            MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, caret,
                    Math.min(text.length(), caret + 1), "");
            assertNotNull(r);
            text = r.text();
            caret = r.caret();
            return this;
        }

        Model replace(int start, int end, String ins) {
            MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, start, end, ins);
            assertNotNull(r, "replace [" + start + "," + end + ") with '" + ins + "' rejected");
            text = r.text();
            caret = r.caret();
            return this;
        }

        Model expect(String expectedText, int expectedCaret) {
            assertEquals(expectedText, text);
            assertEquals(expectedCaret, caret);
            return this;
        }
    }

    private static Model start(String text, int caret) {
        Model m = new Model();
        m.text = text;
        m.caret = caret;
        return m;
    }

    @Test
    void typingDigitsGroupsEveryThreeFromTheRight() {
        new Model().type("2").type("5").type("0").type("0").type("0").type("0").type("0").type("0")
                .expect("25 000 000", 10);
    }

    @Test
    void backspaceFromTheEndShrinksTheGrouping() {
        start("25 000 000", 10).backspace().expect("2 500 000", 9);
        start("2 500 000", 9).backspace().expect("250 000", 7);
        start("250 000", 7).backspace().expect("25 000", 6);
        start("25 000", 6).backspace().expect("2 500", 5);
        start("2 500", 5).backspace().expect("250", 3);
        start("250", 3).backspace().expect("25", 2);
        start("25", 2).backspace().expect("2", 1);
        start("2", 1).backspace().expect("", 0);
        start("", 0).backspace().expect("", 0);
    }

    @Test
    void deleteRemovesTheCharacterUnderTheCaret() {
        // Delete at caret 1 removes the '5' -> "2 000"; Backspace at caret 1 removes the '2' -> "5 000".
        start("25 000", 1).delete().expect("2 000", 1);
        start("25 000", 1).backspace().expect("5 000", 0);
    }

    @Test
    void backspaceOverASpaceRemovesThePreviousDigit() {
        start("25 000", 3).backspace().expect("2 000", 1);
    }

    @Test
    void deleteOverASpaceRemovesTheNextDigit() {
        start("25 000", 2).delete().expect("2 500", 3);
    }

    @Test
    void insertInTheMiddleKeepsTheCaretAfterTheSameDigitCount() {
        // Caret 1 sits after the first digit, so the typed digit lands after it.
        start("2 500", 1).type("1").expect("21 500", 2);
        // Caret 0 sits before the first digit, so the typed digit becomes the new first digit.
        start("2 500", 0).type("1").expect("12 500", 1);
    }

    @Test
    void selectAllAndTypeReplacesEverything() {
        start("25 000", 6).replace(0, 6, "7").expect("7", 1);
        start("25 000", 0).replace(0, 6, "7").expect("7", 1);
    }

    @Test
    void pasteKeepsOnlyTheDigits() {
        new Model().replace(0, 0, "25 000 000 so'm").expect("25 000 000", 10);
        start("1 000", 5).replace(0, 5, "25000000").expect("25 000 000", 10);
        new Model().replace(0, 0, "25,000,000").expect("25 000 000", 10);
    }

    @Test
    void thirteenthDigitIsRejected() {
        start("999 999 999 999", 15).reject("9").expect("999 999 999 999", 15);
        // 12 digits are still fine.
        start("99 999 999 999", 14).type("9").expect("999 999 999 999", 15);
    }

    @Test
    void lettersAreRejected() {
        new Model().reject("a").expect("", 0);
        start("25 000", 6).reject("x").expect("25 000", 6);
        start("25 000", 6).reject("so'm").expect("25 000", 6);
        // A mixed paste keeps its digits.
        start("25 000", 6).type("1abc").expect("250 001", 7);
    }

    @Test
    void leadingZerosCollapseAndASingleZeroStays() {
        new Model().type("0").expect("0", 1);
        start("0", 1).type("0").expect("0", 1);
        start("0", 1).type("7").expect("7", 1);
        new Model().type("0").type("0").type("7").expect("7", 1);
        new Model().replace(0, 0, "000500").expect("500", 3);
    }

    @Test
    void deletingEverythingGivesAnEmptyTextAndCaretZero() {
        start("5", 1).backspace().expect("", 0);
        start("25 000", 6).replace(0, 6, "").expect("", 0);
    }

    @Test
    void rejectedChangeDoesNotAlterAnything() {
        // simulate one rejected paste followed by a valid digit
        Model m = new Model();
        assertNull(MoneyInputFormatter.apply("", 0, 0, 0, "so'm"));
        m.type("7").expect("7", 1);
    }

    @Test
    void randomizedInvariants() {
        Random rnd = new Random(20261006L);
        String text = "";
        int caret = 0;
        int accepted = 0;
        for (int op = 0; op < 8000; op++) {
            switch (rnd.nextInt(6)) {
                case 0 -> {
                    String d = String.valueOf(rnd.nextInt(10));
                    MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, caret, caret, d);
                    if (r != null) {
                        text = r.text();
                        caret = r.caret();
                        accepted++;
                    }
                }
                case 1 -> assertNull(MoneyInputFormatter.apply(text, caret, caret, caret, "x"));
                case 2 -> {
                    MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret,
                            Math.max(0, caret - 1), caret, "");
                    assertNotNull(r);
                    text = r.text();
                    caret = r.caret();
                    accepted++;
                }
                case 3 -> {
                    MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, caret,
                            Math.min(text.length(), caret + 1), "");
                    assertNotNull(r);
                    text = r.text();
                    caret = r.caret();
                    accepted++;
                }
                case 4 -> {
                    StringBuilder sb = new StringBuilder();
                    int n = 1 + rnd.nextInt(14);
                    for (int i = 0; i < n; i++) {
                        sb.append(rnd.nextInt(10));
                    }
                    MoneyInputFormatter.Result r = MoneyInputFormatter.apply(text, caret, caret, caret, sb.toString());
                    if (r != null) {
                        text = r.text();
                        caret = r.caret();
                        accepted++;
                    }
                }
                default -> caret = text.isEmpty() ? 0 : rnd.nextInt(text.length() + 1);
            }
            assertTrue(text.matches("(\\d{1,3}( \\d{3})*)?"), "bad grouped text: '" + text + "'");
            assertTrue(caret >= 0 && caret <= text.length(), "caret out of range: " + caret + " in '" + text + "'");
            String digits = text.replace(" ", "");
            if (!digits.isEmpty()) {
                assertEquals(new BigDecimal(digits), MoneyParser.parse(text),
                        "parsed amount differs from the digits of '" + text + "'");
            }
        }
        assertTrue(accepted > 3000, "expected many accepted operations, got " + accepted);
    }
}
