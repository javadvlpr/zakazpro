package com.mebelsex.report;

import java.math.BigDecimal;

/** One line of the expense report (a catalog item, or the "other" bucket). */
public record ExpenseReportLine(
        String itemName,
        long count,
        BigDecimal total) {
}
