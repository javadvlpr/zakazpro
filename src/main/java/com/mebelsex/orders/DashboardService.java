package com.mebelsex.orders;

import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.EmployeeBalanceService;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentRepository;
import com.mebelsex.report.PeriodReport;
import com.mebelsex.report.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the numbers shown on Bosh sahifa. Reuses OrderFinanceService formulas; no N+1. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int DUE_SOON_LIMIT = 8;
    private static final int DUE_SOON_DAYS = 7;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderFinanceService orderFinanceService;
    private final EmployeeBalanceService employeeBalanceService;
    private final ReportService reportService;

    public DashboardService(OrderRepository orderRepository, PaymentRepository paymentRepository,
            OrderFinanceService orderFinanceService, EmployeeBalanceService employeeBalanceService,
            ReportService reportService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderFinanceService = orderFinanceService;
        this.employeeBalanceService = employeeBalanceService;
        this.reportService = reportService;
    }

    public DashboardSummary getSummary(LocalDate today) {
        List<Order> orders = orderRepository.findAll();
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<Payment>> paymentsByOrder = orderIds.isEmpty()
                ? Collections.emptyMap()
                : paymentRepository.findByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getOrder().getId()));

        LocalDate windowEnd = today.plusDays(DUE_SOON_DAYS);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        int activeOrders = 0;
        int overdueCount = 0;
        BigDecimal totalDebt = BigDecimal.ZERO;
        BigDecimal monthIncome = BigDecimal.ZERO;
        List<OrderListItem> dueSoon = new ArrayList<>();

        for (Order o : orders) {
            OrderStatus status = o.getStatus();
            boolean completed = status == OrderStatus.COMPLETED;
            boolean cancelled = status == OrderStatus.CANCELLED;
            boolean delivered = status == OrderStatus.DELIVERED;
            boolean finished = completed || cancelled || delivered;

            List<Payment> payments = paymentsByOrder.getOrDefault(o.getId(), Collections.emptyList());
            BigDecimal paid = orderFinanceService.calculatePaid(payments);
            BigDecimal debt = cancelled ? BigDecimal.ZERO
                    : orderFinanceService.calculateDebt(o.getAgreedPrice(), paid);

            if (!completed && !cancelled) {
                activeOrders++;
            }
            if (!cancelled) {
                totalDebt = totalDebt.add(debt);
            }

            LocalDate due = o.getDueDate();
            boolean overdue = due != null && due.isBefore(today) && !finished;
            if (overdue) {
                overdueCount++;
            }

            if (!finished && due != null && !due.isAfter(windowEnd)) {
                dueSoon.add(new OrderListItem(
                        o.getId(), o.getOrderNumber(), o.getCustomer().getFullName(),
                        o.getProductDescription(), o.getAgreedPrice(), paid, debt, o.getOrderDate(), due, status, overdue));
            }

            for (Payment p : payments) {
                if (p.getStatus() != RecordStatus.ACTIVE) {
                    continue;
                }
                LocalDate d = p.getPaymentDate();
                if (d == null || d.isBefore(firstOfMonth) || d.isAfter(lastOfMonth)) {
                    continue;
                }
                if (p.getKind() == PaymentKind.REFUND) {
                    monthIncome = monthIncome.subtract(p.getAmount());
                } else {
                    monthIncome = monthIncome.add(p.getAmount());
                }
            }
        }

        dueSoon.sort(Comparator.comparing(OrderListItem::dueDate));
        if (dueSoon.size() > DUE_SOON_LIMIT) {
            dueSoon = new ArrayList<>(dueSoon.subList(0, DUE_SOON_LIMIT));
        }

        BigDecimal workersOwed = employeeBalanceService.totalOwedToWorkers(today);
        PeriodReport monthReport = reportService.periodReport(firstOfMonth, lastOfMonth);
        return new DashboardSummary(activeOrders, totalDebt, overdueCount, monthIncome, dueSoon, workersOwed,
                monthReport.totals().netProfit());
    }
}
