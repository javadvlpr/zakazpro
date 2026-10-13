package com.mebelsex.employees;

import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.orders.DashboardService;
import com.mebelsex.orders.DashboardSummary;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderService;
import com.mebelsex.work.OperationTypeRow;
import com.mebelsex.work.OperationTypeService;
import com.mebelsex.work.SalaryPaymentRow;
import com.mebelsex.work.SalaryPaymentService;
import com.mebelsex.work.WorkLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-balance-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class EmployeeBalanceIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private OperationTypeService operationTypeService;
    @Autowired
    private WorkLogService workLogService;
    @Autowired
    private SalaryPaymentService salaryPaymentService;
    @Autowired
    private EmployeeBalanceService employeeBalanceService;
    @Autowired
    private DashboardService dashboardService;

    private Order newOrder() {
        Customer c = customerService.create("Bal Client " + System.nanoTime(), null, null, null);
        return orderService.create(c.getId(), "Stol", new BigDecimal("10000000"), LocalDate.now().plusDays(5), null,
                null, null, null);
    }

    private EmployeeRow newEmployee(BigDecimal openingBalance) {
        PositionRow position = positionService.create("Lavozim-" + System.nanoTime());
        return employeeService.create("Ishchi " + System.nanoTime(), null, position.id(), openingBalance);
    }

    private OperationTypeRow newOperation(BigDecimal price) {
        return operationTypeService.create("Ish-" + System.nanoTime(), "dona", price);
    }

    @Test
    void balanceUsesOpeningEarnedPaidAndAsOfBoundary() {
        LocalDate today = LocalDate.now();
        EmployeeRow employee = newEmployee(new BigDecimal("5000"));
        OperationTypeRow operation = newOperation(new BigDecimal("1000"));
        Order order = newOrder();

        workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), today,
                BigDecimal.ONE, new BigDecimal("3000"), null);
        workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), today.plusDays(1),
                BigDecimal.ONE, new BigDecimal("7000"), null);

        salaryPaymentService.addPayment(employee.id(), new BigDecimal("1000"), today, null);

        assertEquals(0, employeeBalanceService.balanceAsOf(employee.id(), today)
                .compareTo(new BigDecimal("7000")), "5000 + 3000 - 1000; tomorrow's work excluded");

        assertEquals(0, employeeBalanceService.balanceAsOf(employee.id(), today.plusDays(1))
                .compareTo(new BigDecimal("14000")), "5000 + 3000 + 7000 - 1000");
    }

    @Test
    void balancesAsOfKeepsOpeningEarnedAndPaidSeparate() {
        LocalDate today = LocalDate.now();
        EmployeeRow employee = newEmployee(new BigDecimal("2000"));
        OperationTypeRow operation = newOperation(new BigDecimal("1000"));
        Order order = newOrder();

        workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), today,
                BigDecimal.ONE, new BigDecimal("4000"), null);
        salaryPaymentService.addPayment(employee.id(), new BigDecimal("1500"), today, null);

        EmployeeBalanceRow row = employeeBalanceService.balancesAsOf(today, true).stream()
                .filter(r -> r.employeeId().equals(employee.id())).findFirst().orElseThrow();

        assertEquals(0, row.openingBalance().compareTo(new BigDecimal("2000")));
        assertEquals(0, row.earned().compareTo(new BigDecimal("4000")), "earned excludes opening balance");
        assertEquals(0, row.paid().compareTo(new BigDecimal("1500")));
        assertEquals(0, row.balance().compareTo(new BigDecimal("4500")), "2000 + 4000 - 1500");
    }

    @Test
    void balanceCanBeNegativeWhenPaidMoreThanEarned() {
        LocalDate today = LocalDate.now();
        EmployeeRow employee = newEmployee(BigDecimal.ZERO);
        salaryPaymentService.addPayment(employee.id(), new BigDecimal("9000"), today, "Avans");

        assertEquals(0, employeeBalanceService.balanceAsOf(employee.id(), today)
                .compareTo(new BigDecimal("-9000")));
    }

    @Test
    void totalOwedCountsOnlyPositiveBalances() {
        LocalDate today = LocalDate.now();
        BigDecimal before = employeeBalanceService.totalOwedToWorkers(today);

        newEmployee(new BigDecimal("10000"));
        EmployeeRow owes = newEmployee(BigDecimal.ZERO);
        salaryPaymentService.addPayment(owes.id(), new BigDecimal("5000"), today, null);

        BigDecimal after = employeeBalanceService.totalOwedToWorkers(today);
        assertEquals(0, after.subtract(before).compareTo(new BigDecimal("10000")),
                "only the positive balance (10000) counts; the negative one is ignored");
    }

    @Test
    void inactiveEmployeeMayStillBePaidAndIsListedByName() {
        LocalDate today = LocalDate.now();
        EmployeeRow employee = newEmployee(BigDecimal.ZERO);
        employeeService.setActive(employee.id(), false);

        SalaryPaymentRow payment = salaryPaymentService.addPayment(employee.id(), new BigDecimal("1000"), today, null);
        assertEquals(0, payment.amount().compareTo(new BigDecimal("1000")));

        EmployeeBalanceRow row = employeeBalanceService.balancesAsOf(today, true).stream()
                .filter(r -> r.employeeId().equals(employee.id())).findFirst().orElseThrow();
        assertEquals(employee.fullName(), row.fullName());
        assertEquals(false, row.active());
    }

    @Test
    void salaryPaymentRequiresPositiveAmountAndReasonToVoid() {
        EmployeeRow employee = newEmployee(BigDecimal.ZERO);
        assertThrows(BusinessException.class,
                () -> salaryPaymentService.addPayment(employee.id(), BigDecimal.ZERO, LocalDate.now(), null));

        SalaryPaymentRow payment = salaryPaymentService.addPayment(employee.id(), new BigDecimal("100"),
                LocalDate.now(), null);
        assertThrows(BusinessException.class, () -> salaryPaymentService.voidPayment(payment.id(), "  "));
    }

    @Test
    void dashboardReportsMoneyOwedToWorkers() {
        LocalDate today = LocalDate.now();
        DashboardSummary before = dashboardService.getSummary(today);
        EmployeeRow employee = newEmployee(new BigDecimal("12345"));

        DashboardSummary after = dashboardService.getSummary(today);
        assertEquals(before.workersOwed().add(new BigDecimal("12345")), after.workersOwed());
    }
}
