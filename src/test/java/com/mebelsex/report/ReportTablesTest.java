package com.mebelsex.report;

import com.mebelsex.orders.ExpenseCategoryTotal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ReportTablesTest {

    private static final Function<String, String> LOOKUP = key -> "T:" + key;

    private ReportTables tables() {
        return new ReportTables(LOOKUP);
    }

    private ReportPeriod january() {
        return ReportPeriod.custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void periodReportColumnsRowsAndTotals() {
        PeriodReportRow row = new PeriodReportRow(7L, "1", "Ali", "Stol", LocalDate.of(2026, 1, 10),
                new BigDecimal("100000"), new BigDecimal("20000"), new BigDecimal("30000"),
                new BigDecimal("80000"), new BigDecimal("50000"), new BigDecimal("50.0"),
                new BigDecimal("10000"), new BigDecimal("90000"));
        PeriodReportTotals totals = new PeriodReportTotals(1, new BigDecimal("100000"), new BigDecimal("20000"),
                new BigDecimal("30000"), new BigDecimal("80000"), new BigDecimal("50000"), new BigDecimal("50.0"),
                List.of(new ExpenseCategoryTotal("Material", new BigDecimal("20000"))));
        ReportTable table = tables().periodReport(january(), new PeriodReport(List.of(row), totals));

        assertEquals("T:report.period.title", table.title());
        assertEquals("01.01.2026 \u2013 31.01.2026", table.periodLabel());
        assertEquals(12, table.columns().size());
        assertEquals("T:report.col.order", table.columns().get(0).title());
        assertEquals(CellType.MONEY, table.columns().get(4).type());
        assertEquals(Align.RIGHT, table.columns().get(4).align());

        assertEquals(1, table.rows().size());
        Row r = table.rows().get(0);
        assertEquals(7L, r.orderId());
        assertFalse(r.subtotal());
        assertEquals(new BigDecimal("100000"), r.cells().get(4).value());

        assertTrue(table.totals().subtotal());
        assertEquals(new BigDecimal("100000"), table.totals().cells().get(4).value());
    }

    @Test
    void profitToneIsNegativeWhenNetProfitIsNegative() {
        PeriodReportTotals totals = new PeriodReportTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("-500"), null, List.of());
        ReportTable table = tables().periodReport(january(), new PeriodReport(List.of(), totals));
        SummaryItem net = table.summary().stream()
                .filter(s -> s.label().equals("T:report.period.netProfit")).findFirst().orElseThrow();
        assertEquals(Tone.NEGATIVE, net.tone());
    }

    @Test
    void customerDebtToneIsNegativeWhenPositive() {
        CustomerReport report = new CustomerReport(
                List.of(new CustomerReportRow(3L, "Ali", 1, new BigDecimal("100"), new BigDecimal("40"),
                        new BigDecimal("60"))),
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("60"));
        ReportTable table = tables().customerReport(january(), report);
        SummaryItem debt = table.summary().stream()
                .filter(s -> s.label().equals("T:report.customers.debt")).findFirst().orElseThrow();
        assertEquals(Tone.NEGATIVE, debt.tone());
        assertEquals(1, table.rows().size());
    }

    @Test
    void expenseReportOtherItemsGetBucketLabel() {
        ExpenseReportLine other = new ExpenseReportLine(null, 2, new BigDecimal("1000"));
        ExpenseReport report = new ExpenseReport(
                List.of(new ExpenseReportCategory("Material", List.of(other), new BigDecimal("1000"))), 2,
                new BigDecimal("1000"));
        ReportTable table = tables().expenseReport(january(), report);
        assertEquals("T:report.expenses.other_item", table.rows().get(0).cells().get(1).value());
        assertTrue(table.rows().get(1).subtotal());
    }
}
