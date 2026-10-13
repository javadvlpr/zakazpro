package com.mebelsex.work;

import com.mebelsex.common.RecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-model for a work log row (built inside a transaction). */
public record WorkLogRow(
        Long id,
        LocalDate date,
        Long orderId,
        String orderNumber,
        Long employeeId,
        String employeeName,
        String operationName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String comment,
        RecordStatus status,
        String voidReason) {
}
