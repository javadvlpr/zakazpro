package com.mebelsex.ui.components;

import com.mebelsex.orders.OrderStatus;
import javafx.geometry.Insets;
import javafx.scene.control.Label;

/**
 * Coloured order-status pill. Colours come from the badge-* classes in
 * application.css so both themes and token changes are picked up automatically.
 */
public class StatusBadge extends Label {

    public StatusBadge(OrderStatus status) {
        super(EnumLabels.get(status));
        this.getStyleClass().add("status-badge");
        this.setPadding(new Insets(4, 8, 4, 8));
        this.setStyle("-fx-background-radius: 12px; -fx-font-size: 12px; -fx-font-weight: bold;");

        if (status != null) {
            this.getStyleClass().add("badge-" + status.name().toLowerCase().replace('_', '-'));
        }
    }
}
