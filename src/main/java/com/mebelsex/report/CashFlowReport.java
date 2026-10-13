package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.List;

/** Daily cash-flow report plus totals. Days without movements are omitted. */
public record CashFlowReport(
        List<CashFlowRow> rows,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalWagesPaid,
        BigDecimal totalNet) {
}
