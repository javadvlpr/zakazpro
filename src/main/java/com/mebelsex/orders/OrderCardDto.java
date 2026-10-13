package com.mebelsex.orders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-model for the order card. Built inside a transaction (no lazy loading in controllers). */
public record OrderCardDto(
        Long id,
        String orderNumber,
        String customerName,
        String customerPhone,
        String productDescription,
        BigDecimal agreedPrice,
        LocalDate orderDate,
        LocalDate dueDate,
        OrderStatus status,
        BigDecimal paid,
        BigDecimal debt,
        boolean overdue,
        BigDecimal expensesTotal,
        BigDecimal profitBeforeLabor,
        BigDecimal profitBeforeLaborPercent,
        List<ExpenseCategoryTotal> expenseCategories,
        BigDecimal laborTotal,
        List<LaborEmployeeTotal> laborEmployees,
        BigDecimal netProfit,
        BigDecimal netProfitPercent) {
}
