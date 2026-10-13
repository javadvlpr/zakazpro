package com.mebelsex.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure mapper from report DTOs to {@link ReportTable}. Column/summary titles are
 * resolved through the supplied message lookup, so the mapper stays independent of
 * JavaFX and is easily testable.
 */
public class ReportTables {

    private final Function<String, String> messages;

    public ReportTables(Function<String, String> messages) {
        this.messages = messages;
    }

    private String msg(String key) {
        String value = messages.apply(key);
        return value == null ? key : value;
    }

    private static Tone profitTone(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0 ? Tone.NEGATIVE : Tone.POSITIVE;
    }

    private static Cell money(BigDecimal value) {
        return value == null ? Cell.empty() : Cell.of(value);
    }

    private static Cell percent(BigDecimal value) {
        return value == null ? Cell.empty() : Cell.of(value);
    }

    // ------------------------------------------------------------------
    public ReportTable periodReport(ReportPeriod period, PeriodReport report) {
        List<SummaryItem> summary = new ArrayList<>();
        summary.add(SummaryItem.of(msg("report.period.orders"), Cell.of((long) report.totals().ordersCount()),
                Tone.NEUTRAL));
        summary.add(SummaryItem.of(msg("report.period.sales"), money(report.totals().sales()), Tone.NEUTRAL));
        summary.add(SummaryItem.of(msg("report.period.expenses"), money(report.totals().expenses()), Tone.NEUTRAL));
        summary.add(SummaryItem.of(msg("report.period.labor"), money(report.totals().labor()), Tone.NEUTRAL));
        summary.add(SummaryItem.of(msg("report.period.profitBeforeLabor"),
                money(report.totals().profitBeforeLabor()), profitTone(report.totals().profitBeforeLabor())));
        summary.add(SummaryItem.of(msg("report.period.netProfit"), money(report.totals().netProfit()),
                profitTone(report.totals().netProfit())));

        List<Column> columns = List.of(
                Column.of(msg("report.col.order"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.customer"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.product"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.orderDate"), CellType.DATE, Align.CENTER),
                Column.of(msg("report.col.price"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.expenses"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.labor"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.profitBeforeLabor"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.netProfit"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.netProfitPercent"), CellType.PERCENT, Align.RIGHT),
                Column.of(msg("report.col.paid"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.debt"), CellType.MONEY, Align.RIGHT));

        List<Row> rows = new ArrayList<>();
        for (PeriodReportRow r : report.rows()) {
            List<Cell> cells = List.of(
                    Cell.of(r.orderNumber()),
                    Cell.of(r.customerName()),
                    Cell.of(r.productDescription()),
                    r.orderDate() == null ? Cell.empty() : Cell.of(r.orderDate()),
                    money(r.price()),
                    money(r.expenses()),
                    money(r.labor()),
                    money(r.profitBeforeLabor()),
                    money(r.netProfit()),
                    percent(r.netProfitPercent()),
                    money(r.paid()),
                    money(r.debt()));
            rows.add(Row.data(cells, r.orderId()));
        }

        List<Cell> totalCells = List.of(
                Cell.of(msg("report.total")),
                Cell.empty(),
                Cell.empty(),
                Cell.empty(),
                money(report.totals().sales()),
                money(report.totals().expenses()),
                money(report.totals().labor()),
                money(report.totals().profitBeforeLabor()),
                money(report.totals().netProfit()),
                percent(report.totals().netProfitPercent()),
                Cell.empty(),
                Cell.empty());
        Row totals = Row.subtotal(totalCells);

        return new ReportTable(msg("report.period.title"), period.label(), summary, columns, rows, totals);
    }

    // ------------------------------------------------------------------
    public ReportTable cashFlow(ReportPeriod period, CashFlowReport report) {
        List<SummaryItem> summary = List.of(
                SummaryItem.of(msg("report.cashflow.income"), money(report.totalIncome()), Tone.POSITIVE),
                SummaryItem.of(msg("report.cashflow.expenses"), money(report.totalExpenses()), Tone.NEGATIVE),
                SummaryItem.of(msg("report.cashflow.wages"), money(report.totalWagesPaid()), Tone.NEGATIVE),
                SummaryItem.of(msg("report.cashflow.net"), money(report.totalNet()), profitTone(report.totalNet())));

        List<Column> columns = List.of(
                Column.of(msg("report.col.date"), CellType.DATE, Align.CENTER),
                Column.of(msg("report.col.income"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.expenses"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.wages"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.net"), CellType.MONEY, Align.RIGHT));

        List<Row> rows = new ArrayList<>();
        for (CashFlowRow r : report.rows()) {
            rows.add(Row.plain(List.of(
                    Cell.of(r.date()),
                    money(r.income()),
                    money(r.expenses()),
                    money(r.wagesPaid()),
                    money(r.net()))));
        }

        Row totals = Row.subtotal(List.of(
                Cell.of(msg("report.total")),
                money(report.totalIncome()),
                money(report.totalExpenses()),
                money(report.totalWagesPaid()),
                money(report.totalNet())));

        return new ReportTable(msg("report.cashflow.title"), period.label(), summary, columns, rows, totals);
    }

    // ------------------------------------------------------------------
    public ReportTable expenseReport(ReportPeriod period, ExpenseReport report) {
        List<SummaryItem> summary = List.of(
                SummaryItem.of(msg("report.expenses.count"), Cell.of(report.grandCount()), Tone.NEUTRAL),
                SummaryItem.of(msg("report.expenses.total"), money(report.grandTotal()), Tone.NEGATIVE));

        List<Column> columns = List.of(
                Column.of(msg("report.col.category"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.item"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.count"), CellType.INTEGER, Align.RIGHT),
                Column.of(msg("report.col.total"), CellType.MONEY, Align.RIGHT));

        List<Row> rows = new ArrayList<>();
        for (ExpenseReportCategory category : report.categories()) {
            for (ExpenseReportLine line : category.lines()) {
                String itemName = line.itemName() == null ? msg("report.expenses.other_item") : line.itemName();
                rows.add(Row.plain(List.of(
                        Cell.of(category.categoryName()),
                        Cell.of(itemName),
                        Cell.of(line.count()),
                        money(line.total()))));
            }
            rows.add(Row.subtotal(List.of(
                    Cell.of(category.categoryName()),
                    Cell.of(msg("report.subtotal")),
                    Cell.of(category.lines().stream().mapToLong(ExpenseReportLine::count).sum()),
                    money(category.subtotal()))));
        }

        Row totals = Row.subtotal(List.of(
                Cell.of(msg("report.total")),
                Cell.empty(),
                Cell.of(report.grandCount()),
                money(report.grandTotal())));

        return new ReportTable(msg("report.expenses.title"), period.label(), summary, columns, rows, totals);
    }

    // ------------------------------------------------------------------
    public ReportTable employeeReport(ReportPeriod period, EmployeeReport report) {
        List<SummaryItem> summary = List.of(
                SummaryItem.of(msg("report.employees.total_open"), money(report.totalPositiveClosing()),
                        Tone.NEGATIVE));

        List<Column> columns = List.of(
                Column.of(msg("report.col.employee"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.position"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.opening"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.earned"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.wage_paid"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.closing"), CellType.MONEY, Align.RIGHT));

        List<Row> rows = new ArrayList<>();
        for (EmployeeReportRow r : report.rows()) {
            rows.add(Row.plain(List.of(
                    Cell.of(r.fullName()),
                    r.positionName() == null ? Cell.empty() : Cell.of(r.positionName()),
                    money(r.openingBalance()),
                    money(r.earned()),
                    money(r.paid()),
                    money(r.closingBalance()))));
        }

        return new ReportTable(msg("report.employees.title"), period.label(), summary, columns, rows, null);
    }

    // ------------------------------------------------------------------
    public ReportTable customerReport(ReportPeriod period, CustomerReport report) {
        List<SummaryItem> summary = List.of(
                SummaryItem.of(msg("report.customers.sales"), money(report.totalSales()), Tone.NEUTRAL),
                SummaryItem.of(msg("report.customers.paid"), money(report.totalPaid()), Tone.POSITIVE),
                SummaryItem.of(msg("report.customers.debt"), money(report.totalDebt()),
                        report.totalDebt().compareTo(BigDecimal.ZERO) > 0 ? Tone.NEGATIVE : Tone.POSITIVE));

        List<Column> columns = List.of(
                Column.of(msg("report.col.customer"), CellType.TEXT, Align.LEFT),
                Column.of(msg("report.col.ordersCount"), CellType.INTEGER, Align.RIGHT),
                Column.of(msg("report.col.sales"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.paid"), CellType.MONEY, Align.RIGHT),
                Column.of(msg("report.col.debt"), CellType.MONEY, Align.RIGHT));

        List<Row> rows = new ArrayList<>();
        for (CustomerReportRow r : report.rows()) {
            rows.add(Row.plain(List.of(
                    Cell.of(r.fullName()),
                    Cell.of((long) r.ordersCount()),
                    money(r.sales()),
                    money(r.paid()),
                    money(r.debt()))));
        }

        Row totals = Row.subtotal(List.of(
                Cell.of(msg("report.total")),
                Cell.empty(),
                money(report.totalSales()),
                money(report.totalPaid()),
                money(report.totalDebt())));

        return new ReportTable(msg("report.customers.title"), period.label(), summary, columns, rows, totals);
    }
}
