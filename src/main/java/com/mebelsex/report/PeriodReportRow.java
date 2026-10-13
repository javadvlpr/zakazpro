package com.mebelsex.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One order in the period (profit) report. */
public record PeriodReportRow(
        Long orderId,
        String orderNumber,
        String customerName,
        String productDescription,
        LocalDate orderDate,
        BigDecimal price,
        BigDecimal expenses,
        BigDecimal labor,
        BigDecimal profitBeforeLabor,
        BigDecimal netProfit,
        BigDecimal netProfitPercent,
        BigDecimal paid,
        BigDecimal debt) {
}
