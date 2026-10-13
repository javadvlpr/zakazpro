package com.mebelsex.ui;

import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.ui.components.EnumLabels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Every enum shown in the UI must resolve to a real Uzbek label, never the raw constant name.
 * Covers both the Spring-side mapper and the JavaFX-side static helper used by StatusBadge.
 */
class EnumLabelMappingTest {

    @Test
    void orderStatusBadgeLabelsAreLocalised() {
        for (OrderStatus status : OrderStatus.values()) {
            String label = EnumLabels.get(status);
            assertFalse(label == null || label.isBlank(), "Badge label is blank for " + status);
            assertNotEquals(status.name(), label, "StatusBadge shows the raw enum name for " + status);
        }
    }

    @Test
    void allEnumsHaveNonBlankLocalisedLabels() {
        com.mebelsex.ui.EnumLabels mapper = new com.mebelsex.ui.EnumLabels();

        for (OrderStatus status : OrderStatus.values()) {
            verify(mapper, status);
        }
        for (PaymentKind kind : PaymentKind.values()) {
            verify(mapper, kind);
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            verify(mapper, method);
        }
    }

    private void verify(com.mebelsex.ui.EnumLabels mapper, Enum<?> value) {
        String label = mapper.getLabel(value);
        assertFalse(label == null || label.isBlank(), "Label is blank for " + value);
        assertNotEquals(value.name(), label,
                "Missing translation for " + value.getClass().getSimpleName() + "." + value.name());
    }
}
