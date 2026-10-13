package com.mebelsex.report;

import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.Employee;
import com.mebelsex.employees.EmployeeBalanceService;
import com.mebelsex.employees.EmployeeRepository;
import com.mebelsex.expenses.Expense;
import com.mebelsex.expenses.ExpenseRepository;
import com.mebelsex.orders.ExpenseCategoryTotal;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderFinanceService;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentRepository;
import com.mebelsex.work.SalaryPayment;
import com.mebelsex.work.SalaryPaymentRepository;
import com.mebelsex.work.WorkLog;
import com.mebelsex.work.WorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds the report aggregates. Profit reports are by ORDER DATE (orders with
 * order_date in [from, to], CANCELLED excluded); for those orders ALL their ACTIVE
 * expenses and work logs count, whatever their own date. The cash-flow report is
 * by transaction dates and includes cancelled orders' payments.
 * Balances / profit formulas are reused from OrderFinanceService and EmployeeBalanceService.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final WorkLogRepository workLogRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeBalanceService employeeBalanceService;
    private final OrderFinanceService orderFinanceService;

    public ReportService(OrderRepository orderRepository, PaymentRepository paymentRepository,
            ExpenseRepository expenseRepository, WorkLogRepository workLogRepository,
            SalaryPaymentRepository salaryPaymentRepository, EmployeeRepository employeeRepository,
            EmployeeBalanceService employeeBalanceService, OrderFinanceService orderFinanceService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.workLogRepository = workLogRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.employeeRepository = employeeRepository;
        this.employeeBalanceService = employeeBalanceService;
        this.orderFinanceService = orderFinanceService;
    }

    // ------------------------------------------------------------------
    // 1. Period (profit) report
    // ------------------------------------------------------------------
    public PeriodReport periodReport(LocalDate from, LocalDate to) {
        List<Order> orders = ordersInPeriod(from, to);
        List<Long> ids = orders.stream().map(Order::getId).collect(Collectors.toList());

        Map<Long, List<Payment>> paymentsByOrder = paymentsByOrder(ids);
        Map<Long, BigDecimal> expensesByOrder = new HashMap<>();
        Map<String, BigDecimal> expenseByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<Long, BigDecimal> laborByOrder = new HashMap<>();

        if (!ids.isEmpty()) {
            for (Expense e : expenseRepository.findByOrderIdIn(ids)) {
                if (e.getStatus() != RecordStatus.ACTIVE || e.getAmount() == null || e.getOrder() == null) {
                    continue;
                }
                expensesByOrder.merge(e.getOrder().getId(), e.getAmount(), BigDecimal::add);
                expenseByCategory.merge(e.getCategory().getName(), e.getAmount(), BigDecimal::add);
            }
            for (WorkLog w : workLogRepository.findByStatusAndOrderIdIn(RecordStatus.ACTIVE, ids)) {
                if (w.getAmount() == null || w.getOrder() == null) {
                    continue;
                }
                laborByOrder.merge(w.getOrder().getId(), w.getAmount(), BigDecimal::add);
            }
        }

        List<PeriodReportRow> rows = new ArrayList<>();
        for (Order o : orders) {
            BigDecimal price = o.getAgreedPrice();
            BigDecimal expenses = expensesByOrder.getOrDefault(o.getId(), BigDecimal.ZERO);
            BigDecimal labor = laborByOrder.getOrDefault(o.getId(), BigDecimal.ZERO);
            BigDecimal profitBeforeLabor = price.subtract(expenses);
            BigDecimal netProfit = profitBeforeLabor.subtract(labor);
            BigDecimal paid = orderFinanceService.calculatePaid(
                    paymentsByOrder.getOrDefault(o.getId(), Collections.emptyList()));
            BigDecimal debt = orderFinanceService.calculateDebt(price, paid);
            rows.add(new PeriodReportRow(
                    o.getId(), o.getOrderNumber(), o.getCustomer().getFullName(), o.getProductDescription(),
                    o.getOrderDate(), price, expenses, labor, profitBeforeLabor, netProfit,
                    percent(netProfit, price), paid, debt));
        }

        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalLabor = BigDecimal.ZERO;
        BigDecimal totalProfitBeforeLabor = BigDecimal.ZERO;
        BigDecimal totalNetProfit = BigDecimal.ZERO;
        for (PeriodReportRow r : rows) {
            sales = sales.add(r.price());
            totalExpenses = totalExpenses.add(r.expenses());
            totalLabor = totalLabor.add(r.labor());
            totalProfitBeforeLabor = totalProfitBeforeLabor.add(r.profitBeforeLabor());
            totalNetProfit = totalNetProfit.add(r.netProfit());
        }

        List<ExpenseCategoryTotal> categories = expenseByCategory.entrySet().stream()
                .map(e -> new ExpenseCategoryTotal(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        PeriodReportTotals totals = new PeriodReportTotals(rows.size(), sales, totalExpenses, totalLabor,
                totalProfitBeforeLabor, totalNetProfit, percent(totalNetProfit, sales), categories);
        return new PeriodReport(rows, totals);
    }

    // ------------------------------------------------------------------
    // 2. Cash-flow report (by transaction dates)
    // ------------------------------------------------------------------
    public CashFlowReport cashFlow(LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> income = new HashMap<>();
        Map<LocalDate, BigDecimal> expenses = new HashMap<>();
        Map<LocalDate, BigDecimal> wages = new HashMap<>();

        for (Payment p : paymentRepository.findByStatusAndPaymentDateBetween(RecordStatus.ACTIVE, from, to)) {
            BigDecimal amount = p.getAmount();
            if (p.getKind() == PaymentKind.REFUND) {
                income.merge(p.getPaymentDate(), amount.negate(), BigDecimal::add);
            } else {
                income.merge(p.getPaymentDate(), amount, BigDecimal::add);
            }
        }
        for (Expense e : expenseRepository.findByStatusAndExpenseDateBetween(RecordStatus.ACTIVE, from, to)) {
            expenses.merge(e.getExpenseDate(), e.getAmount(), BigDecimal::add);
        }
        for (SalaryPayment s : salaryPaymentRepository.findByStatusAndPaymentDateBetween(RecordStatus.ACTIVE, from, to)) {
            wages.merge(s.getPaymentDate(), s.getAmount(), BigDecimal::add);
        }

        List<LocalDate> days = new ArrayList<>();
        days.addAll(income.keySet());
        days.addAll(expenses.keySet());
        days.addAll(wages.keySet());
        days = days.stream().distinct().sorted().collect(Collectors.toList());

        List<CashFlowRow> rows = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalWages = BigDecimal.ZERO;
        for (LocalDate day : days) {
            BigDecimal dayIncome = income.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal dayExpenses = expenses.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal dayWages = wages.getOrDefault(day, BigDecimal.ZERO);
            BigDecimal net = dayIncome.subtract(dayExpenses).subtract(dayWages);
            rows.add(new CashFlowRow(day, dayIncome, dayExpenses, dayWages, net));
            totalIncome = totalIncome.add(dayIncome);
            totalExpenses = totalExpenses.add(dayExpenses);
            totalWages = totalWages.add(dayWages);
        }
        BigDecimal totalNet = totalIncome.subtract(totalExpenses).subtract(totalWages);
        return new CashFlowReport(rows, totalIncome, totalExpenses, totalWages, totalNet);
    }

    // ------------------------------------------------------------------
    // 3. Expense report (by expense date, ACTIVE, all orders)
    // ------------------------------------------------------------------
    public ExpenseReport expenseReport(LocalDate from, LocalDate to) {
        Map<String, Map<String, long[]>> counts = new HashMap<>();
        Map<String, Map<String, BigDecimal>> totals = new HashMap<>();

        for (Expense e : expenseRepository.findByStatusAndExpenseDateBetween(RecordStatus.ACTIVE, from, to)) {
            String category = e.getCategory().getName();
            String item = e.getItem() == null ? null : e.getItem().getName();
            String itemKey = item == null ? "" : item;
            counts.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .computeIfAbsent(itemKey, k -> new long[1])[0]++;
            totals.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .merge(itemKey, e.getAmount(), BigDecimal::add);
        }

        List<String> categoryNames = new ArrayList<>(counts.keySet());
        categoryNames.sort(String.CASE_INSENSITIVE_ORDER);

        List<ExpenseReportCategory> categories = new ArrayList<>();
        long grandCount = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (String category : categoryNames) {
            Map<String, long[]> categoryCounts = counts.get(category);
            Map<String, BigDecimal> categoryTotals = totals.get(category);

            List<String> itemKeys = new ArrayList<>(categoryCounts.keySet());
            itemKeys.sort((a, b) -> {
                if (a.isEmpty()) {
                    return 1;
                }
                if (b.isEmpty()) {
                    return -1;
                }
                return a.compareToIgnoreCase(b);
            });

            List<ExpenseReportLine> lines = new ArrayList<>();
            long subtotalCount = 0;
            BigDecimal subtotal = BigDecimal.ZERO;
            for (String itemKey : itemKeys) {
                long count = categoryCounts.get(itemKey)[0];
                BigDecimal total = categoryTotals.getOrDefault(itemKey, BigDecimal.ZERO);
                lines.add(new ExpenseReportLine(itemKey.isEmpty() ? null : itemKey, count, total));
                subtotalCount += count;
                subtotal = subtotal.add(total);
            }
            categories.add(new ExpenseReportCategory(category, lines, subtotal));
            grandCount += subtotalCount;
            grandTotal = grandTotal.add(subtotal);
        }
        return new ExpenseReport(categories, grandCount, grandTotal);
    }

    // ------------------------------------------------------------------
    // 4. Employee report
    // ------------------------------------------------------------------
    public EmployeeReport employeeReport(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> earnedInPeriod = new HashMap<>();
        for (WorkLog w : workLogRepository.findByStatusAndWorkDateLessThanEqual(RecordStatus.ACTIVE, to)) {
            if (w.getWorkDate() != null && !w.getWorkDate().isBefore(from)) {
                earnedInPeriod.merge(w.getEmployee().getId(), w.getAmount(), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> paidInPeriod = new HashMap<>();
        for (SalaryPayment s : salaryPaymentRepository.findByStatusAndPaymentDateLessThanEqual(RecordStatus.ACTIVE, to)) {
            if (s.getPaymentDate() != null && !s.getPaymentDate().isBefore(from)) {
                paidInPeriod.merge(s.getEmployee().getId(), s.getAmount(), BigDecimal::add);
            }
        }

        List<EmployeeReportRow> rows = new ArrayList<>();
        BigDecimal totalPositiveClosing = BigDecimal.ZERO;
        for (Employee employee : employeeRepository.findAllByOrderByFullNameAsc()) {
            Long id = employee.getId();
            BigDecimal opening = employeeBalanceService.balanceAsOf(id, from.minusDays(1));
            BigDecimal closing = employeeBalanceService.balanceAsOf(id, to);
            BigDecimal earned = earnedInPeriod.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal paid = paidInPeriod.getOrDefault(id, BigDecimal.ZERO);
            String positionName = employee.getPosition() == null ? null : employee.getPosition().getName();
            rows.add(new EmployeeReportRow(id, employee.getFullName(), positionName, opening, earned, paid, closing));
            if (closing.compareTo(BigDecimal.ZERO) > 0) {
                totalPositiveClosing = totalPositiveClosing.add(closing);
            }
        }
        return new EmployeeReport(rows, totalPositiveClosing);
    }

    // ------------------------------------------------------------------
    // 5. Customer report
    // ------------------------------------------------------------------
    public CustomerReport customerReport(LocalDate from, LocalDate to) {
        return customerReport(from, to, false);
    }

    public CustomerReport customerReport(LocalDate from, LocalDate to, boolean onlyDebtors) {
        List<Order> orders = ordersInPeriod(from, to);
        List<Long> ids = orders.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, List<Payment>> paymentsByOrder = paymentsByOrder(ids);

        Map<Long, CustomerAccumulator> byCustomer = new LinkedHashMap<>();
        for (Order o : orders) {
            Long customerId = o.getCustomer().getId();
            CustomerAccumulator acc = byCustomer.computeIfAbsent(customerId,
                    k -> new CustomerAccumulator(o.getCustomer().getFullName()));
            acc.ordersCount++;
            acc.sales = acc.sales.add(o.getAgreedPrice());
            acc.paid = acc.paid.add(orderFinanceService.calculatePaid(
                    paymentsByOrder.getOrDefault(o.getId(), Collections.emptyList())));
        }

        List<CustomerReportRow> rows = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        for (Map.Entry<Long, CustomerAccumulator> entry : byCustomer.entrySet()) {
            CustomerAccumulator acc = entry.getValue();
            BigDecimal debt = acc.sales.subtract(acc.paid);
            if (onlyDebtors && debt.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(new CustomerReportRow(entry.getKey(), acc.fullName, acc.ordersCount, acc.sales, acc.paid, debt));
            totalSales = totalSales.add(acc.sales);
            totalPaid = totalPaid.add(acc.paid);
            totalDebt = totalDebt.add(debt);
        }
        rows.sort(Comparator.comparing(CustomerReportRow::fullName, String.CASE_INSENSITIVE_ORDER));
        return new CustomerReport(rows, totalSales, totalPaid, totalDebt);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private List<Order> ordersInPeriod(LocalDate from, LocalDate to) {
        List<Order> orders = new ArrayList<>();
        for (Order o : orderRepository.findAll()) {
            if (o.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            LocalDate orderDate = o.getOrderDate();
            if (orderDate == null || orderDate.isBefore(from) || orderDate.isAfter(to)) {
                continue;
            }
            orders.add(o);
        }
        orders.sort(Comparator.comparingInt(o -> {
            try {
                return Integer.parseInt(o.getOrderNumber());
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));
        return orders;
    }

    private Map<Long, List<Payment>> paymentsByOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return paymentRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getOrder().getId()));
    }

    private BigDecimal percent(BigDecimal profit, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return profit.multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
    }

    private static final class CustomerAccumulator {
        private final String fullName;
        private int ordersCount;
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal paid = BigDecimal.ZERO;

        private CustomerAccumulator(String fullName) {
            this.fullName = fullName;
        }
    }
}
