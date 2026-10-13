package com.mebelsex.ui.components;

/**
 * Pure formatting engine for signed whole-so'm inputs (employee opening balance).
 *
 * <p>
 * Same rules as {@link MoneyInputFormatter}, plus one optional leading minus. "-" alone is a valid
 * intermediate state that parses to {@code null}; typing "-" anywhere makes the value negative and
 * deleting the leading minus makes it positive again. No JavaFX imports.
 */
public final class SignedMoneyInputFormatter {

    private SignedMoneyInputFormatter() {
    }

    public static MoneyInputFormatter.Result apply(String oldText, int oldCaret, int rangeStart, int rangeEnd,
            String inserted) {
        String before = oldText == null ? "" : oldText;
        String ins = inserted == null ? "" : inserted;
        int start = MoneyInputFormatter.clamp(rangeStart, 0, before.length());
        int end = MoneyInputFormatter.clamp(rangeEnd, start, before.length());
        int caret = MoneyInputFormatter.clamp(oldCaret, 0, before.length());

        boolean oldNegative = before.startsWith("-");
        String insertedDigits = MoneyInputFormatter.digitsOf(ins);
        boolean insertedSign = ins.indexOf('-') >= 0;
        if (!ins.isEmpty() && insertedDigits.isEmpty() && !insertedSign) {
            return null;
        }

        // The leading '-' is removed when the change covers it and re-adds no sign.
        boolean signRemoved = oldNegative && start == 0 && end >= 1 && !insertedSign;
        boolean negative = insertedSign || (oldNegative && !signRemoved);

        String oldDigits = MoneyInputFormatter.digitsOf(before);
        int digitsBeforeStart = MoneyInputFormatter.digitCount(before, 0, start);
        int digitsBeforeEnd = MoneyInputFormatter.digitCount(before, 0, end);

        String newDigits = MoneyInputFormatter.splice(oldDigits, digitsBeforeStart, digitsBeforeEnd, insertedDigits);

        int caretDigits;
        if (ins.isEmpty() && start < end && digitsBeforeStart == digitsBeforeEnd) {
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

        newDigits = MoneyInputFormatter.trimLeadingZeros(newDigits);
        if (newDigits.isEmpty()) {
            return negative ? new MoneyInputFormatter.Result("-", 1) : new MoneyInputFormatter.Result("", 0);
        }
        if (newDigits.length() > MoneyInputFormatter.MAX_DIGITS) {
            return null;
        }

        caretDigits = MoneyInputFormatter.clamp(caretDigits, 0, newDigits.length());
        String grouped = MoneyInputFormatter.group(newDigits);
        String text = negative ? "-" + grouped : grouped;
        int caretPos = (negative ? 1 : 0) + MoneyInputFormatter.caretForDigitIndex(grouped, caretDigits);
        return new MoneyInputFormatter.Result(text, caretPos);
    }
}
