package com.mebelsex.expenses;

import java.math.BigDecimal;

/** Read-model for an expense catalog item row (built inside a transaction). */
public record ItemRow(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String unit,
        BigDecimal defaultPrice,
        boolean active) {
}
