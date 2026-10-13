package com.mebelsex.report;

import java.math.BigDecimal;

/** One worker in the employee report. closing = opening + earned - paid. */
public record EmployeeReportRow(
        Long employeeId,
        String fullName,
        String positionName,
        BigDecimal openingBalance,
        BigDecimal earned,
        BigDecimal paid,
        BigDecimal closingBalance) {
}
