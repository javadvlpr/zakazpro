package com.mebelsex.report;

import java.util.List;

/**
 * A fully-built report: title, period label, summary cards, columns, data rows and
 * an optional totals row. Pure data — no JavaFX.
 */
public record ReportTable(
        String title,
        String periodLabel,
        List<SummaryItem> summary,
        List<Column> columns,
        List<Row> rows,
        Row totals) {

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
