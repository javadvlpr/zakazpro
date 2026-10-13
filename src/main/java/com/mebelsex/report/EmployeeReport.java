package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.List;

/** Per-worker balances for a period plus the total of positive closing balances. */
public record EmployeeReport(
        List<EmployeeReportRow> rows,
        BigDecimal totalPositiveClosing) {
}
