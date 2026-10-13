package com.mebelsex.report;

import com.mebelsex.orders.ExpenseCategoryTotal;

import java.math.BigDecimal;
import java.util.List;

/** Totals of the period (profit) report. Equal to the sum of its rows. */
public record PeriodReportTotals(
        int ordersCount,
        BigDecimal sales,
        BigDecimal expenses,
        BigDecimal labor,
        BigDecimal profitBeforeLabor,
        BigDecimal netProfit,
        BigDecimal netProfitPercent,
        List<ExpenseCategoryTotal> expensesByCategory) {
}
