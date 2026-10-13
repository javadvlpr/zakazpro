package com.mebelsex.ui.components;

import com.mebelsex.common.Messages;
import com.mebelsex.orders.OrderStatus;

/**
 * Single implementation of enum -> Uzbek label mapping (used both as a static helper
 * for StatusBadge and through the {@link com.mebelsex.ui.EnumLabels} Spring bean).
 * The label key is {@code <EnumSimpleName>.<NAME>}.
 */
public final class EnumLabels {

    private EnumLabels() {
    }

    public static String get(OrderStatus status) {
        return getLabel(status);
    }

    public static String getLabel(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String key = value.getClass().getSimpleName() + "." + value.name();
        return Messages.contains(key) ? Messages.get(key) : value.name();
    }
}
