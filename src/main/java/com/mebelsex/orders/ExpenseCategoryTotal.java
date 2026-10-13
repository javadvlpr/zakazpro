package com.mebelsex.orders;

import java.math.BigDecimal;

/** Total ACTIVE expenses for one category, used by the order-card Xulosa tab. */
public record ExpenseCategoryTotal(String categoryName, BigDecimal total) {
}
