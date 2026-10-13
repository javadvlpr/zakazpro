package com.mebelsex.ui;

import com.mebelsex.common.RecordStatus;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EnumLabelsTest {

    @Test
    void testAllEnumConstantsHaveLabels() {
        EnumLabels enumLabels = new EnumLabels();

        for (OrderStatus status : OrderStatus.values()) {
            verifyLabel(enumLabels, status);
        }
        for (PaymentKind kind : PaymentKind.values()) {
            verifyLabel(enumLabels, kind);
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            verifyLabel(enumLabels, method);
        }
        for (RecordStatus status : RecordStatus.values()) {
            verifyLabel(enumLabels, status);
        }
    }

    private void verifyLabel(EnumLabels enumLabels, Enum<?> enumValue) {
        String label = enumLabels.getLabel(enumValue);
        assertFalse(label.isEmpty(), "Label for " + enumValue.name() + " is empty");
        assertNotEquals(enumValue.name(), label,
                "Missing translation for " + enumValue.getClass().getSimpleName() + "." + enumValue.name());
    }
}
