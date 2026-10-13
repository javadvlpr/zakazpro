package com.mebelsex.expenses;

import com.mebelsex.common.RecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-model for an order expense row (built inside a transaction). */
public record ExpenseRow(
        Long id,
        LocalDate date,
        String categoryName,
        String itemName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        RecordStatus status,
        String voidReason) {
}
