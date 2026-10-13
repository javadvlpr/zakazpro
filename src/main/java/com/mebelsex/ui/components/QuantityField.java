package com.mebelsex.ui.components;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;

/** TextField for decimal quantities: digits and one '.'/',' separator, at most 3 decimals. */
public class QuantityField extends TextField {

    public QuantityField() {
        super();
        setTextFormatter(new TextFormatter<>(change -> {
            if (!change.isContentChange()) {
                return change;
            }
            MoneyInputFormatter.Result result = QuantityInputFormatter.apply(
                    change.getControlText(),
                    change.getControlCaretPosition(),
                    change.getRangeStart(),
                    change.getRangeEnd(),
                    change.getText());
            return result == null ? null : change;
        }));
    }

    public BigDecimal getQuantity() {
        return QuantityParser.parse(getText());
    }
}
