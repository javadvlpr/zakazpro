package com.mebelsex.employees;

/** Read-model for a position row (built inside a transaction). */
public record PositionRow(Long id, String name, boolean active, long employeeCount) {
}
