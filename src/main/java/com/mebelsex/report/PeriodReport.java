package com.mebelsex.report;

import java.util.List;

/** Period (profit) report: per-order rows plus totals. */
public record PeriodReport(List<PeriodReportRow> rows, PeriodReportTotals totals) {
}
