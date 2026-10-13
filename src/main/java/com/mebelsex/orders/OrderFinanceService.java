package com.mebelsex.orders;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.expenses.Expense;
import com.mebelsex.expenses.ExpenseRepository;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentRepository;
import com.mebelsex.work.WorkLog;
import com.mebelsex.work.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderFinanceService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final WorkLogRepository workLogRepository;

    public OrderFinanceService(OrderRepository orderRepository, PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository, WorkLogRepository workLogRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.workLogRepository = workLogRepository;
    }

    public BigDecimal paidByCustomer(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        return calculatePaid(payments);
    }

    public BigDecimal calculatePaid(List<Payment> payments) {
        BigDecimal paid = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (p.getStatus() == RecordStatus.ACTIVE) {
                if (p.getKind() == PaymentKind.ADVANCE || p.getKind() == PaymentKind.REGULAR) {
                    paid = paid.add(p.getAmount());
                } else if (p.getKind() == PaymentKind.REFUND) {
                    paid = paid.subtract(p.getAmount());
                }
            }
        }
        return paid;
    }

    public BigDecimal customerDebt(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        return calculateDebt(order.getAgreedPrice(), paidByCustomer(orderId));
    }

    public BigDecimal calculateDebt(BigDecimal agreedPrice, BigDecimal paid) {
        return agreedPrice.subtract(paid);
    }

    /** Sum of ACTIVE expenses for an order (VOIDED ignored). */
    public BigDecimal expensesTotal(Long orderId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : expenseRepository.findByOrderId(orderId)) {
            if (e.getStatus() == RecordStatus.ACTIVE && e.getAmount() != null) {
                total = total.add(e.getAmount());
            }
        }
        return total;
    }

    /** ACTIVE expense totals grouped by category name, sorted by name. */
    public List<ExpenseCategoryTotal> expensesByCategory(Long orderId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Expense e : expenseRepository.findByOrderId(orderId)) {
            if (e.getStatus() != RecordStatus.ACTIVE || e.getAmount() == null) {
                continue;
            }
            totals.merge(e.getCategory().getName(), e.getAmount(), BigDecimal::add);
        }
        return totals.entrySet().stream()
                .map(entry -> new ExpenseCategoryTotal(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.categoryName().compareToIgnoreCase(b.categoryName()))
                .collect(Collectors.toList());
    }

    /** agreed_price - expenses_total. */
    public BigDecimal profitBeforeLabor(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        return order.getAgreedPrice().subtract(expensesTotal(orderId));
    }

    /** profit_before_labor / agreed_price * 100; null when agreed_price is 0. */
    public BigDecimal profitBeforeLaborPercent(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        BigDecimal price = order.getAgreedPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal profit = price.subtract(expensesTotal(orderId));
        return profit.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    /** Sum of ACTIVE work log amounts for an order (labor cost); VOIDED ignored. */
    public BigDecimal laborTotal(Long orderId) {
        BigDecimal total = BigDecimal.ZERO;
        for (WorkLog w : workLogRepository.findByOrderId(orderId)) {
            if (w.getStatus() == RecordStatus.ACTIVE && w.getAmount() != null) {
                total = total.add(w.getAmount());
            }
        }
        return total;
    }

    /** ACTIVE labor totals grouped by employee name, sorted by name. */
    public List<LaborEmployeeTotal> laborByEmployee(Long orderId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (WorkLog w : workLogRepository.findByOrderId(orderId)) {
            if (w.getStatus() != RecordStatus.ACTIVE || w.getAmount() == null) {
                continue;
            }
            totals.merge(w.getEmployee().getFullName(), w.getAmount(), BigDecimal::add);
        }
        return totals.entrySet().stream()
                .map(entry -> new LaborEmployeeTotal(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.employeeName().compareToIgnoreCase(b.employeeName()))
                .collect(Collectors.toList());
    }

    /** profit_before_labor - labor_total. */
    public BigDecimal netProfit(Long orderId) {
        return profitBeforeLabor(orderId).subtract(laborTotal(orderId));
    }

    /** net_profit / agreed_price * 100; null when agreed_price is 0. */
    public BigDecimal netProfitPercent(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        BigDecimal price = order.getAgreedPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal profit = price.subtract(expensesTotal(orderId)).subtract(laborTotal(orderId));
        return profit.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    public List<Order> findOrdersWithDebt() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> customerDebt(o.getId()).compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }
}
