package com.mebelsex.report;

import com.mebelsex.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class ReportPeriodTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Test
    void todayIsASingleDay() {
        ReportPeriod p = ReportPeriod.today();
        assertEquals(LocalDate.now(), p.from());
        assertEquals(LocalDate.now(), p.to());
        assertEquals(DATE.format(LocalDate.now()), p.label());
    }

    @Test
    void thisWeekStartsOnMonday() {
        ReportPeriod p = ReportPeriod.thisWeek();
        assertEquals(DayOfWeek.MONDAY, p.from().getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, p.to().getDayOfWeek());
        assertEquals(p.from().plusDays(6), p.to());
    }

    @Test
    void thisMonthCoversWholeMonth() {
        ReportPeriod p = ReportPeriod.thisMonth();
        LocalDate now = LocalDate.now();
        assertEquals(now.withDayOfMonth(1), p.from());
        assertEquals(now.withDayOfMonth(now.lengthOfMonth()), p.to());
    }

    @Test
    void previousMonthEndsBeforeThisMonth() {
        ReportPeriod p = ReportPeriod.previousMonth();
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        assertEquals(firstOfThisMonth.minusDays(1), p.to());
        assertEquals(p.to().withDayOfMonth(1), p.from());
        assertTrue(p.from().isBefore(p.to()));
    }

    @Test
    void thisYearCoversWholeYear() {
        ReportPeriod p = ReportPeriod.thisYear();
        LocalDate now = LocalDate.now();
        assertEquals(now.withDayOfYear(1), p.from());
        assertEquals(now.withDayOfYear(now.lengthOfYear()), p.to());
    }

    @Test
    void customValidates() {
        ReportPeriod p = ReportPeriod.custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertEquals("01.01.2026 \u2013 31.01.2026", p.label());

        assertThrows(BusinessException.class,
                () -> ReportPeriod.custom(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(BusinessException.class, () -> ReportPeriod.custom(null, LocalDate.now()));
        assertThrows(BusinessException.class, () -> ReportPeriod.custom(LocalDate.now(), null));
    }
}
