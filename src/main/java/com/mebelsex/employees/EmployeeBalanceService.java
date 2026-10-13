package com.mebelsex.employees;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.work.SalaryPayment;
import com.mebelsex.work.SalaryPaymentRepository;
import com.mebelsex.work.WorkLog;
import com.mebelsex.work.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker balances. This is the ONLY place where balance logic lives.
 * balance(date) = opening_balance + sum(ACTIVE work_logs.amount with work_date <= date)
 *                 - sum(ACTIVE salary_payments.amount with payment_date <= date).
 * Positive = the workshop owes the worker; negative = the worker owes the workshop.
 * Balances are never stored. Bulk queries are grouped in memory to avoid N+1.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeBalanceService {

    private final EmployeeRepository employeeRepository;
    private final WorkLogRepository workLogRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;

    public EmployeeBalanceService(EmployeeRepository employeeRepository, WorkLogRepository workLogRepository,
            SalaryPaymentRepository salaryPaymentRepository) {
        this.employeeRepository = employeeRepository;
        this.workLogRepository = workLogRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
    }

    /** Balance of one worker as of the given date (inclusive). */
    public BigDecimal balanceAsOf(Long employeeId, LocalDate date) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("employee.error.notfound"));

        BigDecimal earned = BigDecimal.ZERO;
        for (WorkLog workLog : workLogRepository.findByEmployeeId(employeeId)) {
            if (workLog.getStatus() == RecordStatus.ACTIVE && countsUpTo(workLog.getWorkDate(), date)) {
                earned = earned.add(workLog.getAmount());
            }
        }

        BigDecimal paid = BigDecimal.ZERO;
        for (SalaryPayment payment : salaryPaymentRepository.findByEmployeeId(employeeId)) {
            if (payment.getStatus() == RecordStatus.ACTIVE && countsUpTo(payment.getPaymentDate(), date)) {
                paid = paid.add(payment.getAmount());
            }
        }

        return employee.getOpeningBalance().add(earned).subtract(paid);
    }

    /** All workers with earned/paid/balance as of the given date (inclusive). */
    public List<EmployeeBalanceRow> balancesAsOf(LocalDate date, boolean includeInactive) {
        Map<Long, BigDecimal> earnedByEmployee = earnedByEmployee(date);
        Map<Long, BigDecimal> paidByEmployee = paidByEmployee(date);

        List<EmployeeBalanceRow> rows = new ArrayList<>();
        for (Employee employee : employeeRepository.findAllByOrderByFullNameAsc()) {
            if (!includeInactive && !employee.isActive()) {
                continue;
            }
            BigDecimal earned = earnedByEmployee.getOrDefault(employee.getId(), BigDecimal.ZERO);
            BigDecimal paid = paidByEmployee.getOrDefault(employee.getId(), BigDecimal.ZERO);
            BigDecimal opening = employee.getOpeningBalance();
            BigDecimal balance = opening.add(earned).subtract(paid);
            Position position = employee.getPosition();
            rows.add(new EmployeeBalanceRow(
                    employee.getId(),
                    employee.getFullName(),
                    position == null ? null : position.getName(),
                    employee.isActive(),
                    opening,
                    earned,
                    paid,
                    balance));
        }
        return rows;
    }

    /** Sum of positive balances as of the date (money the workshop owes to workers). */
    public BigDecimal totalOwedToWorkers(LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (EmployeeBalanceRow row : balancesAsOf(date, true)) {
            if (row.balance().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(row.balance());
            }
        }
        return total;
    }

    private boolean countsUpTo(LocalDate recordDate, LocalDate date) {
        return recordDate != null && (date == null || !recordDate.isAfter(date));
    }

    private Map<Long, BigDecimal> earnedByEmployee(LocalDate date) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (date == null) {
            return result;
        }
        for (WorkLog workLog : workLogRepository.findByStatusAndWorkDateLessThanEqual(RecordStatus.ACTIVE, date)) {
            result.merge(workLog.getEmployee().getId(), workLog.getAmount(), BigDecimal::add);
        }
        return result;
    }

    private Map<Long, BigDecimal> paidByEmployee(LocalDate date) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (date == null) {
            return result;
        }
        for (SalaryPayment payment : salaryPaymentRepository
                .findByStatusAndPaymentDateLessThanEqual(RecordStatus.ACTIVE, date)) {
            result.merge(payment.getEmployee().getId(), payment.getAmount(), BigDecimal::add);
        }
        return result;
    }
}
