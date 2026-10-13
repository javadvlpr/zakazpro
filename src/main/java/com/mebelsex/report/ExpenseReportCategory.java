package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.List;

/** One expense category with its lines and subtotal. */
public record ExpenseReportCategory(
        String categoryName,
        List<ExpenseReportLine> lines,
        BigDecimal subtotal) {
}
