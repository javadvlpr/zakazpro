package com.mebelsex.backup;

import java.time.LocalDateTime;

/**
 * Result of inspecting a candidate backup file. When valid is false, reasonKey
 * holds an i18n key explaining the problem.
 */
public record RestorePreview(
        boolean valid,
        String reasonKey,
        LocalDateTime fileTime,
        String schemaVersion,
        long customers,
        long orders,
        long payments,
        long currentCustomers,
        long currentOrders,
        long currentPayments) {

    public static RestorePreview invalid(String reasonKey, long currentCustomers, long currentOrders,
            long currentPayments) {
        return new RestorePreview(false, reasonKey, null, null, 0, 0, 0, currentCustomers, currentOrders,
                currentPayments);
    }
}
