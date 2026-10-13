package com.mebelsex.work;

import com.mebelsex.common.RecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-model for a salary payment row (built inside a transaction). */
public record SalaryPaymentRow(
        Long id,
        LocalDate date,
        BigDecimal amount,
        String comment,
        RecordStatus status,
        String voidReason) {
}
