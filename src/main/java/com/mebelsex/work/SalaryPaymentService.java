package com.mebelsex.work;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.Employee;
import com.mebelsex.employees.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wage payments (ish haqi to'lovlari). Not linked to orders; may push the balance negative.
 * Nothing is deleted: VOIDED + reason. Inactive workers may still be paid.
 */
@Service
@Transactional(readOnly = true)
public class SalaryPaymentService {

    private final SalaryPaymentRepository salaryPaymentRepository;
    private final EmployeeRepository employeeRepository;

    public SalaryPaymentService(SalaryPaymentRepository salaryPaymentRepository,
            EmployeeRepository employeeRepository) {
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public SalaryPaymentRow addPayment(Long employeeId, BigDecimal amount, LocalDate date, String comment) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("employee.error.notfound"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("salary.error.invalid_amount");
        }
        if (date == null) {
            throw new BusinessException("salary.error.date_required");
        }

        SalaryPayment payment = new SalaryPayment();
        payment.setEmployee(employee);
        payment.setAmount(amount);
        payment.setPaymentDate(date);
        payment.setComment(comment == null ? null : comment.trim());
        return toRow(salaryPaymentRepository.save(payment));
    }

    @Transactional
    public void voidPayment(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("salary.error.void_reason_required");
        }
        SalaryPayment payment = salaryPaymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("salary.error.notfound"));
        if (payment.getStatus() == RecordStatus.VOIDED) {
            throw new BusinessException("salary.error.already_voided");
        }
        payment.setStatus(RecordStatus.VOIDED);
        payment.setVoidReason(reason.trim());
        salaryPaymentRepository.save(payment);
    }

    /** Newest first; optional inclusive date range; VOIDED rows are included. */
    public List<SalaryPaymentRow> listForEmployee(Long employeeId, LocalDate from, LocalDate to) {
        return salaryPaymentRepository.findByEmployeeId(employeeId).stream()
                .filter(p -> from == null || !p.getPaymentDate().isBefore(from))
                .filter(p -> to == null || !p.getPaymentDate().isAfter(to))
                .sorted(Comparator
                        .comparing(SalaryPayment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SalaryPayment::getId, Comparator.reverseOrder()))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    private SalaryPaymentRow toRow(SalaryPayment payment) {
        return new SalaryPaymentRow(
                payment.getId(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getComment(),
                payment.getStatus(),
                payment.getVoidReason());
    }
}
