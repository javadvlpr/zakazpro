package com.mebelsex.employees;

import java.math.BigDecimal;

/**
 * Read-model for the Ishchilar screen and the employee card.
 * balance = openingBalance + earned - paid (positive = the workshop owes the worker).
 */
public record EmployeeBalanceRow(
        Long employeeId,
        String fullName,
        String positionName,
        boolean active,
        BigDecimal openingBalance,
        BigDecimal earned,
        BigDecimal paid,
        BigDecimal balance) {
}
