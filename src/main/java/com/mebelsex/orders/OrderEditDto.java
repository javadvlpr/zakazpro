package com.mebelsex.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-model for the order edit form. Built inside a transaction so the UI never
 * touches a lazy association (e.g. Order.customer) from the JavaFX thread.
 */
public record OrderEditDto(
        Long id,
        Long customerId,
        String customerName,
        String customerPhone,
        String productDescription,
        BigDecimal agreedPrice,
        LocalDate orderDate,
        LocalDate dueDate,
        String notes,
        BigDecimal paid) {
}
