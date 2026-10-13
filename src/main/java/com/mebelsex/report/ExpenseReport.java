package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.List;

/** Expenses in a period grouped by category then item, with subtotals and a grand total. */
public record ExpenseReport(
        List<ExpenseReportCategory> categories,
        long grandCount,
        BigDecimal grandTotal) {
}
