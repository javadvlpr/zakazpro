package com.mebelsex.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderListItem(
        Long id,
        String orderNumber,
        String customerName,
        String productDescription,
        BigDecimal agreedPrice,
        BigDecimal paid,
        BigDecimal debt,
        LocalDate orderDate,
        LocalDate dueDate,
        OrderStatus status,
        boolean overdue) {
}
