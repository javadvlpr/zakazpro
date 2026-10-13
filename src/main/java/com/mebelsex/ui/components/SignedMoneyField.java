package com.mebelsex.ui.components;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Money field that accepts a leading minus (used only for the employee opening balance). Like
 * {@link MoneyField}, grouping is applied inside a single {@link TextFormatter} filter instead of a
 * text listener that mutates the same control.
 */
public class SignedMoneyField extends TextField {

    public SignedMoneyField() {
        super();
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (!change.isContentChange()) {
                return change;
            }
            MoneyInputFormatter.Result result = SignedMoneyInputFormatter.apply(
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
        return SignedMoneyParser.parse(getText());
    }
}
