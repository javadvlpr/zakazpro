package com.mebelsex.work;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.employees.EmployeeRow;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.employees.PositionRow;
import com.mebelsex.employees.PositionService;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderFinanceService;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderService;
import com.mebelsex.orders.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-work-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class WorkLogIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderFinanceService orderFinanceService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private OperationTypeService operationTypeService;
    @Autowired
    private WorkLogService workLogService;

    private Order newOrder(String price) {
        Customer c = customerService.create("Work Client " + System.nanoTime(), null, null, null);
        return orderService.create(c.getId(), "Divan", new BigDecimal(price), LocalDate.now().plusDays(5), null,
                null, null, null);
    }

    private EmployeeRow newEmployee() {
        PositionRow position = positionService.create("Lavozim-" + System.nanoTime());
        return employeeService.create("Ishchi " + System.nanoTime(), "+998900000000", position.id(), null);
    }

    private OperationTypeRow newOperation(String price) {
        return operationTypeService.create("Ish-" + System.nanoTime(), "m²", new BigDecimal(price));
    }

    @Test
    void amountIsQuantityTimesPriceRoundedHalfUp() {
        Order order = newOrder("25000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("85000");

        WorkLogRow row = workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                new BigDecimal("15.5"), new BigDecimal("85000"), null);

        assertEquals(0, row.amount().compareTo(new BigDecimal("1317500")), "15.5 x 85000");
        assertEquals(0, row.unitPrice().compareTo(new BigDecimal("85000")));
        assertEquals(0, row.quantity().compareTo(new BigDecimal("15.5")));
        assertEquals(RecordStatus.ACTIVE, row.status());
        assertEquals(order.getOrderNumber(), row.orderNumber());
        assertEquals(employee.fullName(), row.employeeName());
    }

    @Test
    void unitPriceAndAmountAreFrozenAfterCatalogPriceChange() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("100000");

        WorkLogRow row = workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                new BigDecimal("2"), new BigDecimal("100000"), null);

        operationTypeService.update(operation.id(), operation.name(), "m²", new BigDecimal("999000"));

        WorkLogRow reloaded = workLogService.listForOrder(order.getId()).stream()
                .filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
        assertEquals(0, reloaded.unitPrice().compareTo(new BigDecimal("100000")), "old unit price kept");
        assertEquals(0, reloaded.amount().compareTo(new BigDecimal("200000")), "old amount kept");
    }

    @Test
    void voidedWorkLogsAreExcludedFromLaborTotal() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("100000");

        workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                BigDecimal.ONE, new BigDecimal("300000"), "Saqlanadi");
        WorkLogRow drop = workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                BigDecimal.ONE, new BigDecimal("500000"), "Xato");

        workLogService.voidWorkLog(drop.id(), "Xato kiritildi");

        assertEquals(0, orderFinanceService.laborTotal(order.getId()).compareTo(new BigDecimal("300000")));
        assertTrue(workLogService.listForOrder(order.getId()).stream()
                .anyMatch(r -> r.id().equals(drop.id()) && r.status() == RecordStatus.VOIDED), "VOIDED retained");
    }

    @Test
    void voidRequiresReason() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("1000");
        WorkLogRow row = workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                BigDecimal.ONE, new BigDecimal("1000"), null);
        assertThrows(BusinessException.class, () -> workLogService.voidWorkLog(row.id(), "   "));
    }

    @Test
    void cancelledOrderRejectsWorkLogs() {
        Order order = newOrder("10000000");
        orderService.changeStatus(order.getId(), OrderStatus.CANCELLED);
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("1000");
        assertThrows(BusinessException.class, () -> workLogService.addWorkLog(order.getId(), employee.id(),
                operation.id(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("1000"), null));
    }

    @Test
    void inactiveEmployeeAndOperationAreRejected() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("1000");

        employeeService.setActive(employee.id(), false);
        assertThrows(BusinessException.class, () -> workLogService.addWorkLog(order.getId(), employee.id(),
                operation.id(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("1000"), null));

        employeeService.setActive(employee.id(), true);
        operationTypeService.setActive(operation.id(), false);
        assertThrows(BusinessException.class, () -> workLogService.addWorkLog(order.getId(), employee.id(),
                operation.id(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("1000"), null));
    }

    @Test
    void quantityMustBePositiveAndPriceNotNegative() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("1000");

        assertThrows(BusinessException.class, () -> workLogService.addWorkLog(order.getId(), employee.id(),
                operation.id(), LocalDate.now(), BigDecimal.ZERO, new BigDecimal("1000"), null));
        assertThrows(BusinessException.class, () -> workLogService.addWorkLog(order.getId(), employee.id(),
                operation.id(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("-1"), null));
    }

    @Test
    void laborTotalNetProfitAndPercentWithZeroPrice() {
        Order order = newOrder("10000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("3000");

        workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                BigDecimal.ONE, new BigDecimal("3000"), null);

        assertEquals(0, orderFinanceService.laborTotal(order.getId()).compareTo(new BigDecimal("3000")));
        assertEquals(0, orderFinanceService.netProfit(order.getId()).compareTo(new BigDecimal("7000")));
        assertEquals(0, orderFinanceService.netProfitPercent(order.getId()).compareTo(new BigDecimal("70.0")));

        List<com.mebelsex.orders.LaborEmployeeTotal> byEmployee = orderFinanceService
                .laborByEmployee(order.getId());
        assertEquals(1, byEmployee.size());
        assertEquals(0, byEmployee.get(0).total().compareTo(new BigDecimal("3000")));
    }

    @Test
    void netProfitPercentIsNullWhenPriceIsZero() {
        Order order = newOrder("1000");
        Order stored = orderRepository.findById(order.getId()).orElseThrow();
        stored.setAgreedPrice(BigDecimal.ZERO);
        orderRepository.save(stored);
        assertNull(orderFinanceService.netProfitPercent(order.getId()));
    }

    @Test
    void operationTypeNamesAreUniqueCaseInsensitive() {
        String name = "Kesish-" + System.nanoTime();
        operationTypeService.create(name, "m²", BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> operationTypeService.create(name.toUpperCase(), "m²",
                BigDecimal.ZERO));
    }

    @Test
    void deactivatedEmployeeStillAppearsByNameInOldRows() {
        Order order = newOrder("10000000");
        EmployeeRow employee = newEmployee();
        OperationTypeRow operation = newOperation("1000");
        WorkLogRow row = workLogService.addWorkLog(order.getId(), employee.id(), operation.id(), LocalDate.now(),
                BigDecimal.ONE, new BigDecimal("1000"), null);

        employeeService.setActive(employee.id(), false);

        WorkLogRow reloaded = workLogService.listForOrder(order.getId()).stream()
                .filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
        assertEquals(employee.fullName(), reloaded.employeeName());
    }
}
