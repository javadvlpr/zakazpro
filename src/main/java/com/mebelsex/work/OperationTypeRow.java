package com.mebelsex.work;

import java.math.BigDecimal;

/** Read-model for an operation type row (built inside a transaction). */
public record OperationTypeRow(
        Long id,
        String name,
        String unit,
        BigDecimal defaultPrice,
        boolean active) {
}
