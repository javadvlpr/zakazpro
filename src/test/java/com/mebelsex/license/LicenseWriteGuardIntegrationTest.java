package com.mebelsex.license;

import com.mebelsex.backup.BackupReason;
import com.mebelsex.backup.BackupService;
import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.employees.PositionService;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.expenses.ExpenseService;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderService;
import com.mebelsex.settings.SettingsService;
import com.mebelsex.ui.ExceptionHandler;
import com.mebelsex.work.OperationTypeService;
import com.mebelsex.work.WorkLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** READ_ONLY and BLOCKED must block every business write but keep settings and backup working. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-license-guard-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class LicenseWriteGuardIntegrationTest {

    @TempDir
    Path temp;

    @Autowired
    private ToggleableLicenseGate gate;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ExpenseCatalogService catalogService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private OperationTypeService operationTypeService;
    @Autowired
    private WorkLogService workLogService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private BackupService backupService;

    @BeforeEach
    void resetGate() {
        gate.reset();
    }

    @AfterEach
    void resetGateAfter() {
        gate.reset();
    }

    @Test
    void readOnlyBlocksEveryBusinessWriteButKeepsSettingsAndBackup() {
        Customer customer = customerService.create("Bloklanmagan mijoz", null, null, null);
        Order order = orderService.create(customer.getId(), "Stol", new BigDecimal("5000000"),
                LocalDate.now().plusDays(5), null, null, null, LocalDate.now());
        var category = catalogService.createCategory("Sinov kategoriyasi");
        var item = catalogService.createItem(category.id(), "Fanera", "m2", new BigDecimal("80000"));
        var position = positionService.create("Sinov lavozimi");
        var employee = employeeService.create("Sinov ishchi", null, position.id(), null);
        var operation = operationTypeService.create("Sinov ishi", "m2", new BigDecimal("25000"));

        gate.setState(LicenseStatus.State.READ_ONLY, LicenseStatusCalculator.REASON_EXPIRED);

        assertBlocked(() -> customerService.create("Boshqa", null, null, null));
        assertBlocked(() -> orderService.create(customer.getId(), "Shkaf", new BigDecimal("1000000"),
                LocalDate.now().plusDays(2), null, null, null, LocalDate.now()));
        assertBlocked(() -> expenseService.addExpense(order.getId(), category.id(), item.id(), null,
                new BigDecimal("2"), new BigDecimal("80000"), LocalDate.now()));
        assertBlocked(() -> workLogService.addWorkLog(order.getId(), employee.id(), operation.id(),
                LocalDate.now(), new BigDecimal("1"), new BigDecimal("25000"), null));

        // Reads keep working.
        assertTrue(orderService.findById(order.getId()).isPresent());

        // Settings stay writable, so the app can still report and renew.
        settingsService.saveTheme("DARK");
        settingsService.saveBackup(temp.resolve("backups").toString(), false, "00:00", false, 60);

        // Backup (VACUUM INTO) still works.
        assertNotNull(backupService.createBackup(BackupReason.MANUAL));

        // Back to VALID: writes work again.
        gate.reset();
        assertNotNull(customerService.create("Yana ishlaydi", null, null, null));
    }

    @Test
    void blockedStateAlsoBlocksWrites() {
        gate.setState(LicenseStatus.State.BLOCKED, LicenseStatusCalculator.REASON_MISSING);
        assertBlocked(() -> customerService.create("Bloklangan", null, null, null));
        settingsService.saveTheme("LIGHT");
        gate.reset();
    }

    private void assertBlocked(Runnable action) {
        Throwable thrown = assertThrows(Throwable.class, action::run);
        BusinessException business = ExceptionHandler.findBusinessException(thrown);
        assertNotNull(business, "expected an unwrapped BusinessException, got: " + thrown);
        assertEquals("license.error.readonly", business.getMessageKey());
    }
}
