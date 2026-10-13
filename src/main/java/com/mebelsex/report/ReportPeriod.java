package com.mebelsex.report;

import com.mebelsex.common.BusinessException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/** A report period [from, to] (inclusive) with a display label. Pure (no JavaFX, no DB). */
public record ReportPeriod(LocalDate from, LocalDate to, String label) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static ReportPeriod today() {
        LocalDate t = LocalDate.now();
        return new ReportPeriod(t, t, DATE.format(t));
    }

    public static ReportPeriod thisWeek() {
        LocalDate t = LocalDate.now();
        LocalDate monday = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        return range(monday, sunday);
    }

    public static ReportPeriod thisMonth() {
        LocalDate t = LocalDate.now();
        return range(t.withDayOfMonth(1), t.withDayOfMonth(t.lengthOfMonth()));
    }

    public static ReportPeriod previousMonth() {
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastOfPrevious = firstOfThisMonth.minusDays(1);
        return range(lastOfPrevious.withDayOfMonth(1), lastOfPrevious);
    }

    public static ReportPeriod thisYear() {
        LocalDate t = LocalDate.now();
        return range(t.withDayOfYear(1), t.withDayOfYear(t.lengthOfYear()));
    }

    public static ReportPeriod custom(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("report.error.period_required");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("report.error.period_invalid");
        }
        return range(from, to);
    }

    private static ReportPeriod range(LocalDate from, LocalDate to) {
        String label = from.equals(to)
                ? DATE.format(from)
                : DATE.format(from) + " \u2013 " + DATE.format(to);
        return new ReportPeriod(from, to, label);
    }
}
