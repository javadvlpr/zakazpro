package com.mebelsex.report;

import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.employees.EmployeeRow;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.expenses.ExpenseRow;
import com.mebelsex.expenses.ExpenseService;
import com.mebelsex.orders.ExpenseCategoryTotal;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderService;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.payments.PaymentService;
import com.mebelsex.work.OperationTypeRow;
import com.mebelsex.work.OperationTypeService;
import com.mebelsex.work.SalaryPaymentService;
import com.mebelsex.work.WorkLogRow;
import com.mebelsex.work.WorkLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-report-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class ReportServiceIntegrationTest {

    @Autowired
    private ReportService reportService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ExpenseCatalogService expenseCatalogService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private OperationTypeService operationTypeService;
    @Autowired
    private WorkLogService workLogService;
    @Autowired
    private SalaryPaymentService salaryPaymentService;
    @Autowired
    private OrderRepository orderRepository;

    private Customer customer(String name) {
        return customerService.create(name, null, null, null);
    }

    private Order order(Customer c, String product, String price, LocalDate orderDate) {
        return orderService.create(c.getId(), product, new BigDecimal(price), orderDate.plusDays(20), orderDate,
                null, null, null, null);
    }

    private Long materialCategoryId() {
        return categoryId("Material");
    }

    private Long categoryId(String name) {
        return expenseCatalogService.listActiveCategories().stream()
                .filter(c -> c.name().equals(name)).map(CategoryRow::id).findFirst().orElseThrow();
    }

    private BigDecimal categoryTotal(PeriodReportTotals totals, String name) {
        Optional<ExpenseCategoryTotal> found = totals.expensesByCategory().stream()
                .filter(t -> t.categoryName().equals(name)).findFirst();
        return found.map(ExpenseCategoryTotal::total).orElse(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    @Test
    void periodReport_usesOrderDate_excludesCancelled_sumsRows() {
        Customer c = customer("Davr Mijozi");
        Long material = materialCategoryId();

        // A: order in period; expense/work/payment OUTSIDE the period must still count.
        Order a = order(c, "Stol", "100000", LocalDate.of(2026, 1, 10));
        expenseService.addExpense(a.getId(), material, null, "Mix", BigDecimal.ONE, new BigDecimal("20000"),
                LocalDate.of(2026, 2, 20));
        expenseService.addExpense(a.getId(), material, null, "Void", BigDecimal.ONE, new BigDecimal("9000"),
                LocalDate.of(2026, 1, 20));
        Long voidedExpenseId = expenseService.listRows(a.getId()).stream()
                .filter(e -> "Void".equals(e.itemName())).findFirst().orElseThrow().id();
        expenseService.voidExpense(voidedExpenseId, "Xato");

        EmployeeRow emp = employeeService.create("Davr Ishchi", null, null, BigDecimal.ZERO);
        OperationTypeRow op = operationTypeService.create("Kesish", "m2", BigDecimal.ZERO);
        WorkLogRow wl = workLogService.addWorkLog(a.getId(), emp.id(), op.id(), LocalDate.of(2026, 3, 5),
                BigDecimal.ONE, new BigDecimal("30000"), null);
        assertEquals(new BigDecimal("30000"), wl.amount());

        paymentService.addPayment(a.getId(), new BigDecimal("15000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 2, 1), null);
        paymentService.addPayment(a.getId(), new BigDecimal("5000"), PaymentKind.REFUND, PaymentMethod.CASH,
                LocalDate.of(2026, 2, 2), null);

        // B: boundary last day.
        Order b = order(c, "Divan", "50000", LocalDate.of(2026, 1, 31));
        paymentService.addPayment(b.getId(), new BigDecimal("20000"), PaymentKind.ADVANCE, PaymentMethod.CASH,
                LocalDate.of(2026, 1, 31), null);

        // C: order OUTSIDE the period; its expense/payment inside must be ignored by the profit report.
        Order cOrder = order(c, "Shkaf", "80000", LocalDate.of(2026, 2, 10));
        expenseService.addExpense(cOrder.getId(), material, null, "Tashqari", BigDecimal.ONE, new BigDecimal("5000"),
                LocalDate.of(2026, 1, 15));
        workLogService.addWorkLog(cOrder.getId(), emp.id(), op.id(), LocalDate.of(2026, 1, 15), BigDecimal.ONE,
                new BigDecimal("7000"), null);
        paymentService.addPayment(cOrder.getId(), new BigDecimal("3000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 1, 12), null);

        // D: CANCELLED order in period -> excluded from the profit report.
        Order d = order(c, "Karavot", "40000", LocalDate.of(2026, 1, 15));
        paymentService.addPayment(d.getId(), new BigDecimal("7000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 1, 16), null);
        orderService.changeStatus(d.getId(), OrderStatus.CANCELLED);

        PeriodReport report = reportService.periodReport(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(2, report.rows().size());
        PeriodReportRow rowA = report.rows().stream().filter(r -> r.orderId().equals(a.getId())).findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("100000"), rowA.price());
        assertEquals(new BigDecimal("20000"), rowA.expenses());
        assertEquals(new BigDecimal("30000"), rowA.labor());
        assertEquals(new BigDecimal("80000"), rowA.profitBeforeLabor());
        assertEquals(new BigDecimal("50000"), rowA.netProfit());
        assertEquals(new BigDecimal("50.0"), rowA.netProfitPercent());
        assertEquals(new BigDecimal("10000"), rowA.paid());
        assertEquals(new BigDecimal("90000"), rowA.debt());
        assertEquals(LocalDate.of(2026, 1, 10), rowA.orderDate());

        PeriodReportRow rowB = report.rows().stream().filter(r -> r.orderId().equals(b.getId())).findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("50000"), rowB.netProfit());
        assertEquals(new BigDecimal("100.0"), rowB.netProfitPercent());
        assertEquals(new BigDecimal("20000"), rowB.paid());
        assertEquals(new BigDecimal("30000"), rowB.debt());

        PeriodReportTotals totals = report.totals();
        assertEquals(2, totals.ordersCount());
        assertEquals(new BigDecimal("150000"), totals.sales());
        assertEquals(new BigDecimal("20000"), totals.expenses());
        assertEquals(new BigDecimal("30000"), totals.labor());
        assertEquals(new BigDecimal("130000"), totals.profitBeforeLabor());
        assertEquals(new BigDecimal("100000"), totals.netProfit());
        assertEquals(new BigDecimal("66.7"), totals.netProfitPercent());
        assertEquals(new BigDecimal("20000"), categoryTotal(totals, "Material"));

        // Totals equal the sum of the rows.
        assertEquals(totals.sales(), report.rows().stream().map(PeriodReportRow::price).reduce(BigDecimal.ZERO,
                BigDecimal::add));
        assertEquals(totals.netProfit(), report.rows().stream().map(PeriodReportRow::netProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void periodReport_percentIsNullWhenPriceIsZero() {
        Customer c = customer("Nol Mijozi");
        Order o = order(c, "Sovg'a", "1", LocalDate.of(2026, 1, 5));
        Order stored = orderRepository.findById(o.getId()).orElseThrow();
        stored.setAgreedPrice(BigDecimal.ZERO);
        orderRepository.save(stored);

        PeriodReport report = reportService.periodReport(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        PeriodReportRow row = report.rows().get(0);
        assertNull(row.netProfitPercent());
    }

    @Test
    void cashFlow_byTransactionDates_dailyRowsAndTotals() {
        Customer c = customer("Kassa Mijozi");
        Long material = materialCategoryId();

        Order g = order(c, "Stol", "100000", LocalDate.of(2026, 4, 1));
        paymentService.addPayment(g.getId(), new BigDecimal("10000"), PaymentKind.ADVANCE, PaymentMethod.CASH,
                LocalDate.of(2026, 4, 1), null);
        paymentService.addPayment(g.getId(), new BigDecimal("5000"), PaymentKind.REGULAR, PaymentMethod.CARD,
                LocalDate.of(2026, 4, 30), null);
        paymentService.addPayment(g.getId(), new BigDecimal("2000"), PaymentKind.REFUND, PaymentMethod.CASH,
                LocalDate.of(2026, 4, 15), null);
        expenseService.addExpense(g.getId(), material, null, "Kley", BigDecimal.ONE, new BigDecimal("4000"),
                LocalDate.of(2026, 4, 10));

        // Voided payment ignored.
        Payment voided = paymentService.addPayment(g.getId(), new BigDecimal("900"), PaymentKind.REGULAR,
                PaymentMethod.CASH, LocalDate.of(2026, 4, 12), null);
        paymentService.voidPayment(voided.getId(), "Xato");

        // Cancelled order still counts in cash flow.
        Order h = order(c, "Divan", "50000", LocalDate.of(2026, 4, 5));
        paymentService.addPayment(h.getId(), new BigDecimal("3000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 4, 10), null);
        orderService.changeStatus(h.getId(), OrderStatus.CANCELLED);

        // Payment outside the period ignored.
        paymentService.addPayment(g.getId(), new BigDecimal("1000"), PaymentKind.ADVANCE, PaymentMethod.CASH,
                LocalDate.of(2026, 5, 5), null);

        EmployeeRow emp = employeeService.create("Kassa Ishchi", null, null, BigDecimal.ZERO);
        salaryPaymentService.addPayment(emp.id(), new BigDecimal("6000"), LocalDate.of(2026, 4, 20), null);

        CashFlowReport report = reportService.cashFlow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertEquals(5, report.rows().size());
        CashFlowRow apr1 = row(report, LocalDate.of(2026, 4, 1));
        assertEquals(new BigDecimal("10000"), apr1.income());
        assertEquals(new BigDecimal("10000"), apr1.net());

        CashFlowRow apr10 = row(report, LocalDate.of(2026, 4, 10));
        assertEquals(new BigDecimal("3000"), apr10.income());
        assertEquals(new BigDecimal("4000"), apr10.expenses());
        assertEquals(new BigDecimal("-1000"), apr10.net());

        CashFlowRow apr15 = row(report, LocalDate.of(2026, 4, 15));
        assertEquals(new BigDecimal("-2000"), apr15.income());

        CashFlowRow apr20 = row(report, LocalDate.of(2026, 4, 20));
        assertEquals(new BigDecimal("6000"), apr20.wagesPaid());
        assertEquals(new BigDecimal("-6000"), apr20.net());

        CashFlowRow apr30 = row(report, LocalDate.of(2026, 4, 30));
        assertEquals(new BigDecimal("5000"), apr30.income());

        assertEquals(new BigDecimal("16000"), report.totalIncome());
        assertEquals(new BigDecimal("4000"), report.totalExpenses());
        assertEquals(new BigDecimal("6000"), report.totalWagesPaid());
        assertEquals(new BigDecimal("6000"), report.totalNet());
    }

    @Test
    void expenseReport_groupsByCategoryAndItem() {
        Customer c = customer("Xarajat Mijozi");
        Long material = materialCategoryId();
        Long transport = categoryId("Transport");
        var item = expenseCatalogService.createItem(material, "Mix", "kg", new BigDecimal("1000"));

        Order o = order(c, "Stol", "100000", LocalDate.of(2026, 6, 5));
        expenseService.addExpense(o.getId(), material, item.id(), null, BigDecimal.ONE, new BigDecimal("2000"),
                LocalDate.of(2026, 6, 10));
        expenseService.addExpense(o.getId(), material, item.id(), null, BigDecimal.ONE, new BigDecimal("3000"),
                LocalDate.of(2026, 6, 11));
        expenseService.addExpense(o.getId(), material, null, "Boshqa narsa", BigDecimal.ONE, new BigDecimal("1000"),
                LocalDate.of(2026, 6, 12));
        expenseService.addExpense(o.getId(), transport, null, "Yetkazish", BigDecimal.ONE, new BigDecimal("4000"),
                LocalDate.of(2026, 6, 15));

        ExpenseRow voided = expenseService.addExpense(o.getId(), material, null, "Void", BigDecimal.ONE,
                new BigDecimal("7000"), LocalDate.of(2026, 6, 16));
        expenseService.voidExpense(voided.id(), "Xato");

        ExpenseReport report = reportService.expenseReport(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(2, report.categories().size());
        ExpenseReportCategory mat = report.categories().stream()
                .filter(x -> x.categoryName().equals("Material")).findFirst().orElseThrow();
        assertEquals(2, mat.lines().size());
        ExpenseReportLine mix = mat.lines().stream().filter(l -> "Mix".equals(l.itemName())).findFirst().orElseThrow();
        assertEquals(2, mix.count());
        assertEquals(new BigDecimal("5000"), mix.total());
        ExpenseReportLine other = mat.lines().stream().filter(l -> l.itemName() == null).findFirst().orElseThrow();
        assertEquals(1, other.count());
        assertEquals(new BigDecimal("1000"), other.total());
        assertEquals(new BigDecimal("6000"), mat.subtotal());

        ExpenseReportCategory tr = report.categories().stream()
                .filter(x -> x.categoryName().equals("Transport")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("4000"), tr.subtotal());

        assertEquals(4, report.grandCount());
        assertEquals(new BigDecimal("10000"), report.grandTotal());
    }

    @Test
    void employeeReport_invariantHolds() {
        Customer c = customer("Ishchi Mijozi");
        EmployeeRow emp = employeeService.create("Hisobot Ishchi", null, null, new BigDecimal("1000"));
        OperationTypeRow op = operationTypeService.create("Yig'ish", "dona", BigDecimal.ZERO);

        Order o = order(c, "Stol", "100000", LocalDate.of(2026, 8, 5));
        // Before the period: earned 2000, paid 1000 -> opening (as of Jul 31) = 1000 + 2000 - 1000 = 2000.
        workLogService.addWorkLog(o.getId(), emp.id(), op.id(), LocalDate.of(2026, 7, 1), BigDecimal.ONE,
                new BigDecimal("2000"), null);
        salaryPaymentService.addPayment(emp.id(), new BigDecimal("1000"), LocalDate.of(2026, 7, 15), null);
        // In period: earned 5000, paid 3000 -> closing = 4000.
        workLogService.addWorkLog(o.getId(), emp.id(), op.id(), LocalDate.of(2026, 8, 10), BigDecimal.ONE,
                new BigDecimal("5000"), null);
        salaryPaymentService.addPayment(emp.id(), new BigDecimal("3000"), LocalDate.of(2026, 8, 20), null);

        EmployeeReport report = reportService.employeeReport(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        EmployeeReportRow row = report.rows().stream().filter(r -> r.employeeId().equals(emp.id())).findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("2000"), row.openingBalance());
        assertEquals(new BigDecimal("5000"), row.earned());
        assertEquals(new BigDecimal("3000"), row.paid());
        assertEquals(new BigDecimal("4000"), row.closingBalance());
        assertEquals(row.openingBalance().add(row.earned()).subtract(row.paid()), row.closingBalance());
        assertEquals(new BigDecimal("4000"), report.totalPositiveClosing());
    }

    @Test
    void customerReport_andOnlyDebtorsFilter() {
        Customer a = customer("Ali Mijoz");
        Order a1 = order(a, "Stol", "100000", LocalDate.of(2026, 8, 5));
        paymentService.addPayment(a1.getId(), new BigDecimal("30000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 8, 6), null);
        Order a2 = order(a, "Divan", "50000", LocalDate.of(2026, 8, 20));
        paymentService.addPayment(a2.getId(), new BigDecimal("50000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 8, 21), null);

        // Cancelled order with a payment -> excluded from the customer report.
        Order a3 = order(a, "Karavot", "20000", LocalDate.of(2026, 8, 25));
        paymentService.addPayment(a3.getId(), new BigDecimal("5000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 8, 26), null);
        orderService.changeStatus(a3.getId(), OrderStatus.CANCELLED);

        Customer b = customer("Bek Mijoz");
        Order b1 = order(b, "Shkaf", "10000", LocalDate.of(2026, 8, 7));
        paymentService.addPayment(b1.getId(), new BigDecimal("10000"), PaymentKind.REGULAR, PaymentMethod.CASH,
                LocalDate.of(2026, 8, 8), null);

        CustomerReport all = reportService.customerReport(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertEquals(2, all.rows().size());
        CustomerReportRow rowA = all.rows().stream().filter(r -> r.customerId().equals(a.getId())).findFirst()
                .orElseThrow();
        assertEquals(2, rowA.ordersCount());
        assertEquals(new BigDecimal("150000"), rowA.sales());
        assertEquals(new BigDecimal("80000"), rowA.paid());
        assertEquals(new BigDecimal("70000"), rowA.debt());
        assertEquals(new BigDecimal("160000"), all.totalSales());
        assertEquals(new BigDecimal("90000"), all.totalPaid());
        assertEquals(new BigDecimal("70000"), all.totalDebt());

        CustomerReport debtors = reportService.customerReport(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                true);
        assertEquals(1, debtors.rows().size());
        assertEquals(a.getId(), debtors.rows().get(0).customerId());
    }

    private CashFlowRow row(CashFlowReport report, LocalDate date) {
        return report.rows().stream().filter(r -> r.date().equals(date)).findFirst().orElseThrow();
    }
}
