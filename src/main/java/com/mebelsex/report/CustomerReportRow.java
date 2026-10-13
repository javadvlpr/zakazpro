package com.mebelsex.report;

import java.math.BigDecimal;

/** One customer in the customer report (orders whose order_date is in the period). */
public record CustomerReportRow(
        Long customerId,
        String fullName,
        int ordersCount,
        BigDecimal sales,
        BigDecimal paid,
        BigDecimal debt) {
}
