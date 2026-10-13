package com.mebelsex.employees;

import java.math.BigDecimal;

/** Read-model for an employee row / edit form (built inside a transaction). */
public record EmployeeRow(
        Long id,
        String fullName,
        String phone,
        Long positionId,
        String positionName,
        boolean active,
        BigDecimal openingBalance) {
}
