package com.mebelsex.ui.components;

import java.util.regex.Pattern;

/**
 * Pure validation engine for decimal quantity inputs (no grouping). Digits plus at most one '.'
 * or ',' separator and at most three decimals; empty and a lone separator are valid intermediate
 * states. No JavaFX imports, so it can be unit-tested directly.
 */
public final class QuantityInputFormatter {

    private static final Pattern SHAPE = Pattern.compile("\\d*([.,]\\d{0,3})?");

    private QuantityInputFormatter() {
    }

    /** The raw text that the change would produce, or {@code null} when it is not a valid shape. */
    public static MoneyInputFormatter.Result apply(String oldText, int oldCaret, int rangeStart, int rangeEnd,
            String inserted) {
        String before = oldText == null ? "" : oldText;
        String ins = inserted == null ? "" : inserted;
        int start = MoneyInputFormatter.clamp(rangeStart, 0, before.length());
        int end = MoneyInputFormatter.clamp(rangeEnd, start, before.length());
        String next = before.substring(0, start) + ins + before.substring(end);
        if (!accepts(next)) {
            return null;
        }
        return new MoneyInputFormatter.Result(next, start + ins.length());
    }

    /** True when the text is a shape a quantity field may hold. */
    public static boolean accepts(String text) {
        return text != null && SHAPE.matcher(text).matches();
    }
}
