package com.mebelsex.expenses;

import com.mebelsex.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByOrderId(Long orderId);

    List<Expense> findByOrderIdIn(List<Long> orderIds);

    List<Expense> findByStatusAndExpenseDateBetween(RecordStatus status, LocalDate from, LocalDate to);
}
