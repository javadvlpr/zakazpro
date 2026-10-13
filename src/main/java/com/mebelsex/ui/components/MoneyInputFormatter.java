package com.mebelsex.ui.components;

/**
 * Pure formatting engine for whole-so'm money inputs (no JavaFX imports, so it can be unit-tested).
 *
 * <p>
 * Money is shown grouped with a space every three digits from the right ("25 000 000"). The
 * formatter receives the raw change that JavaFX is about to apply to a {@code TextField} and
 * returns the grouped text plus the new caret, or {@code null} when the change must be rejected.
 * It never touches a JavaFX control, so a control can be reformatted from inside its own
 * {@code TextFormatter} filter, which is the only place JavaFX allows it.
 *
 * <p>
 * Rules: digits only (maximum {@value #MAX_DIGITS}); grouping is a space every three digits;
 * leading zeros collapse ("007" -&gt; "7", a single "0" stays); inserted text with no digit is
 * rejected (typing a letter does nothing); a longer paste keeps only its digits
 * ("25 000 000 so'm" -&gt; "25 000 000"); the caret is placed after the same number of digits that
 * were before the caret; Backspace over a separator removes the previous digit, Delete over a
 * separator removes the next one; deleting everything yields an empty text with caret 0.
 */
public final class MoneyInputFormatter {

    /** Maximum number of digits accepted (12 digits = up to 999 999 999 999). */
    public static final int MAX_DIGITS = 12;

    /** Result of an accepted change: the new (grouped) text and the caret position in it. */
    public record Result(String text, int caret) {
    }

    private MoneyInputFormatter() {
    }

    /**
     * @param oldText    the full text of the control before the change
     * @param oldCaret   the caret position before the change
     * @param rangeStart start of the replaced range (in {@code oldText})
     * @param rangeEnd   end of the replaced range (in {@code oldText})
     * @param inserted   the text being inserted (empty for a pure deletion)
     * @return the new text/caret, or {@code null} to reject the change
     */
    public static Result apply(String oldText, int oldCaret, int rangeStart, int rangeEnd, String inserted) {
        String before = oldText == null ? "" : oldText;
        String ins = inserted == null ? "" : inserted;
        int start = clamp(rangeStart, 0, before.length());
        int end = clamp(rangeEnd, start, before.length());
        int caret = clamp(oldCaret, 0, before.length());

        String insertedDigits = digitsOf(ins);
        if (!ins.isEmpty() && insertedDigits.isEmpty()) {
            return null;
        }

        String oldDigits = digitsOf(before);
        int digitsBeforeStart = digitCount(before, 0, start);
        int digitsBeforeEnd = digitCount(before, 0, end);

        String newDigits = splice(oldDigits, digitsBeforeStart, digitsBeforeEnd, insertedDigits);

        int caretDigits;
        if (ins.isEmpty() && start < end && digitsBeforeStart == digitsBeforeEnd) {
            // The change removed only separators: Backspace takes the previous digit, Delete the next.
            int removeAt = caret == end ? digitsBeforeStart - 1 : (caret == start ? digitsBeforeStart : -1);
            if (removeAt >= 0 && removeAt < newDigits.length()) {
                newDigits = newDigits.substring(0, removeAt) + newDigits.substring(removeAt + 1);
                caretDigits = removeAt;
            } else {
                caretDigits = digitsBeforeStart;
            }
        } else {
            caretDigits = digitsBeforeStart + insertedDigits.length();
        }

        newDigits = trimLeadingZeros(newDigits);
        if (newDigits.isEmpty()) {
            return new Result("", 0);
        }
        if (newDigits.length() > MAX_DIGITS) {
            return null;
        }

        caretDigits = clamp(caretDigits, 0, newDigits.length());
        String grouped = group(newDigits);
        return new Result(grouped, caretForDigitIndex(grouped, caretDigits));
    }

    /** Every digit of the text, in order. */
    static String digitsOf(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Number of digits in {@code text} between {@code from} and {@code to}. */
    static int digitCount(String text, int from, int to) {
        int count = 0;
        for (int i = Math.max(0, from); i < Math.min(to, text.length()); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                count++;
            }
        }
        return count;
    }

    /** Replaces the digits in {@code [from, to)} of {@code digits} with {@code inserted}. */
    static String splice(String digits, int from, int to, String inserted) {
        int f = clamp(from, 0, digits.length());
        int t = clamp(to, f, digits.length());
        return digits.substring(0, f) + inserted + digits.substring(t);
    }

    /** Removes leading zeros but keeps a single "0" when there was any digit. */
    static String trimLeadingZeros(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') {
            i++;
        }
        return digits.substring(i);
    }

    /** Groups digits with a space every three characters from the right. */
    static String group(String digits) {
        if (digits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3);
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /** Caret position in {@code grouped} that sits after {@code digitIndex} digits. */
    static int caretForDigitIndex(String grouped, int digitIndex) {
        int count = 0;
        for (int i = 0; i < grouped.length(); i++) {
            if (count == digitIndex) {
                return i;
            }
            char c = grouped.charAt(i);
            if (c >= '0' && c <= '9') {
                count++;
            }
        }
        return grouped.length();
    }

    static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
