package com.mebelsex.expenses;

/** Read-model for an expense category row (built inside a transaction). */
public record CategoryRow(Long id, String name, boolean active, long itemCount) {
}
