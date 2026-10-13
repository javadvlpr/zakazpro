package com.mebelsex.orders;

import java.math.BigDecimal;
import java.util.List;

/** Read-model for the Bosh sahifa dashboard. */
public record DashboardSummary(
        int activeOrders,
        BigDecimal totalDebt,
        int overdueCount,
        BigDecimal monthIncome,
        List<OrderListItem> dueSoon,
        BigDecimal workersOwed,
        BigDecimal monthNetProfit) {
}
