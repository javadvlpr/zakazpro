package com.mebelsex.ui.components;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Whole-so'm money field. Grouping (a space every three digits) is applied inside a single
 * {@link TextFormatter} filter. The control is never modified from its own text listener: JavaFX
 * forbids that and it caused
 * {@code java.lang.IllegalArgumentException: The start must be <= the end} on Backspace.
 */
public class MoneyField extends TextField {

    public MoneyField() {
        super();
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (!change.isContentChange()) {
                return change;
            }
            MoneyInputFormatter.Result result = MoneyInputFormatter.apply(
                    change.getControlText(),
                    change.getControlCaretPosition(),
                    change.getRangeStart(),
                    change.getRangeEnd(),
                    change.getText());
            if (result == null) {
                return null;
            }
            change.setRange(0, change.getControlText().length());
            change.setText(result.text());
            change.selectRange(result.caret(), result.caret());
            return change;
        };
        setTextFormatter(new TextFormatter<>(filter));
    }

    public BigDecimal getAmount() {
        return MoneyParser.parse(getText());
    }
}
