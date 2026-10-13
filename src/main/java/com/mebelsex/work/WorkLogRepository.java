package com.mebelsex.work;

import com.mebelsex.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    List<WorkLog> findByOrderId(Long orderId);

    List<WorkLog> findByStatusAndOrderIdIn(RecordStatus status, List<Long> orderIds);

    List<WorkLog> findByEmployeeId(Long employeeId);

    /** Bulk fetch used by EmployeeBalanceService (one query, grouped in memory) to avoid N+1. */
    List<WorkLog> findByStatusAndWorkDateLessThanEqual(RecordStatus status, LocalDate date);
}
