package com.mebelsex.payments;

import com.mebelsex.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(List<Long> orderIds);

    List<Payment> findByStatusAndPaymentDateBetween(RecordStatus status, LocalDate from, LocalDate to);
}
