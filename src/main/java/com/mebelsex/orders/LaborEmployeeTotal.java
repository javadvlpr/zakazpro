package com.mebelsex.orders;

import java.math.BigDecimal;

/** ACTIVE labor total for one employee within an order (built inside a transaction). */
public record LaborEmployeeTotal(String employeeName, BigDecimal total) {
}
