package com.mebelsex.payments;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderFinanceService;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderFinanceService orderFinanceService;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
            OrderFinanceService orderFinanceService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderFinanceService = orderFinanceService;
    }

    @Transactional
    public Payment addPayment(Long orderId, BigDecimal amount, PaymentKind kind, PaymentMethod method, LocalDate date,
            String comment) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("payment.error.invalid_amount");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("payment.error.order_cancelled");
        }

        if (kind == PaymentKind.ADVANCE || kind == PaymentKind.REGULAR) {
            BigDecimal debt = orderFinanceService.customerDebt(orderId);
            if (amount.compareTo(debt) > 0) {
                throw new BusinessException("payment.error.exceeds_debt");
            }
        } else if (kind == PaymentKind.REFUND) {
            BigDecimal paid = orderFinanceService.paidByCustomer(orderId);
            if (amount.compareTo(paid) > 0) {
                throw new BusinessException("payment.error.exceeds_paid");
            }
        }

        Payment payment = new Payment(order, amount, kind, method, date, comment);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment voidPayment(Long paymentId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("payment.error.void_reason_required");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("payment.error.notfound"));

        if (payment.getStatus() == RecordStatus.VOIDED) {
            throw new BusinessException("payment.error.already_voided");
        }

        payment.setStatus(RecordStatus.VOIDED);
        payment.setVoidReason(reason.trim());
        return paymentRepository.save(payment);
    }

    public List<Payment> listByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /** Newest first (by payment date, then id); VOIDED rows are included. */
    public List<PaymentRow> listRows(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .sorted(Comparator
                        .comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Payment::getId, Comparator.reverseOrder()))
                .map(p -> new PaymentRow(
                        p.getId(),
                        p.getPaymentDate(),
                        p.getKind(),
                        p.getMethod(),
                        p.getAmount(),
                        p.getComment(),
                        p.getStatus(),
                        p.getVoidReason()))
                .collect(Collectors.toList());
    }
}
