package com.mebelsex.work;

import com.mebelsex.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {

    List<SalaryPayment> findByEmployeeId(Long employeeId);

    List<SalaryPayment> findByStatusAndPaymentDateBetween(RecordStatus status, LocalDate from, LocalDate to);

    /** Bulk fetch used by EmployeeBalanceService (one query) to avoid N+1. */
    List<SalaryPayment> findByStatusAndPaymentDateLessThanEqual(RecordStatus status, LocalDate date);
}
