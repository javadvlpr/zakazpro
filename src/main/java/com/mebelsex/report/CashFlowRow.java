package com.mebelsex.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day with movements in the cash-flow report. */
public record CashFlowRow(
        LocalDate date,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal wagesPaid,
        BigDecimal net) {
}
