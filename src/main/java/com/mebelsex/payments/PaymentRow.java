package com.mebelsex.payments;

import com.mebelsex.common.RecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-model for the payments table of an order card. */
public record PaymentRow(
        Long id,
        LocalDate date,
        PaymentKind kind,
        PaymentMethod method,
        BigDecimal amount,
        String comment,
        RecordStatus status,
        String voidReason) {
}
