package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.List;

/** Customer report rows plus totals. */
public record CustomerReport(
        List<CustomerReportRow> rows,
        BigDecimal totalSales,
        BigDecimal totalPaid,
        BigDecimal totalDebt) {
}
