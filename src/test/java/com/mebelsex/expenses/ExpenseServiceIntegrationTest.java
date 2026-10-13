package com.mebelsex.expenses;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
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
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-exp-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class ExpenseServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderFinanceService orderFinanceService;
    @Autowired
    private ExpenseCatalogService catalogService;
    @Autowired
    private ExpenseService expenseService;

    private Order newOrder(String product, String price) {
        Customer c = customerService.create("Exp Client " + System.nanoTime(), null, null, null);
        return orderService.create(c.getId(), product, new BigDecimal(price), LocalDate.now().plusDays(5), null,
                null, null, null);
    }

    private CategoryRow newCategory() {
        return catalogService.createCategory("Cat-" + System.nanoTime());
    }

    @Test
    void amountIsQuantityTimesPriceRoundedHalfUp() {
        Order order = newOrder("Stol", "25000000");
        CategoryRow category = newCategory();
        ItemRow item = catalogService.createItem(category.id(), "Fanera-" + System.nanoTime(), "m²",
                new BigDecimal("85000"));

        ExpenseRow row = expenseService.addExpense(order.getId(), category.id(), item.id(), null,
                new BigDecimal("15.5"), new BigDecimal("85000"), LocalDate.now());

        assertEquals(0, row.amount().compareTo(new BigDecimal("1317500")), "15.5 x 85000 = 1 317 500");
        assertEquals(0, row.unitPrice().compareTo(new BigDecimal("85000")));
        assertEquals(0, row.quantity().compareTo(new BigDecimal("15.5")));
        assertEquals(RecordStatus.ACTIVE, row.status());
    }

    @Test
    void unitPriceAndAmountAreFrozenAfterCatalogPriceChange() {
        Order order = newOrder("Shkaf", "10000000");
        CategoryRow category = newCategory();
        ItemRow item = catalogService.createItem(category.id(), "Lak-" + System.nanoTime(), "litr",
                new BigDecimal("100000"));

        ExpenseRow row = expenseService.addExpense(order.getId(), category.id(), item.id(), null,
                new BigDecimal("2"), new BigDecimal("100000"), LocalDate.now());

        catalogService.updateItem(item.id(), category.id(), item.name(), "litr", new BigDecimal("999000"));

        ExpenseRow reloaded = expenseService.listRows(order.getId()).stream()
                .filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
        assertEquals(0, reloaded.unitPrice().compareTo(new BigDecimal("100000")), "old unit price kept");
        assertEquals(0, reloaded.amount().compareTo(new BigDecimal("200000")), "old amount kept");
    }

    @Test
    void voidedExpensesAreExcludedFromTotals() {
        Order order = newOrder("Parta", "10000000");
        CategoryRow category = newCategory();
        ExpenseRow keep = expenseService.addExpense(order.getId(), category.id(), null, "Yog'och",
                BigDecimal.ONE, new BigDecimal("2000"), LocalDate.now());
        ExpenseRow drop = expenseService.addExpense(order.getId(), category.id(), null, "Xato",
                BigDecimal.ONE, new BigDecimal("1500"), LocalDate.now());

        expenseService.voidExpense(drop.id(), "Xato kiritildi");

        assertEquals(0, orderFinanceService.expensesTotal(order.getId()).compareTo(new BigDecimal("2000")));
        assertTrue(expenseService.listRows(order.getId()).stream()
                .anyMatch(r -> r.id().equals(drop.id()) && r.status() == RecordStatus.VOIDED), "VOIDED row retained");
        assertEquals(0, keep.amount().compareTo(new BigDecimal("2000")));
    }

    @Test
    void voidRequiresReason() {
        Order order = newOrder("Stul", "5000000");
        CategoryRow category = newCategory();
        ExpenseRow row = expenseService.addExpense(order.getId(), category.id(), null, "Mix",
                BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now());
        assertThrows(BusinessException.class, () -> expenseService.voidExpense(row.id(), "  "));
    }

    @Test
    void cancelledOrderRejectsExpenses() {
        Order order = newOrder("Divan", "8000000");
        orderService.changeStatus(order.getId(), OrderStatus.CANCELLED);
        CategoryRow category = newCategory();
        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), category.id(), null,
                "Yog'och", BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now()));
    }

    @Test
    void inactiveCategoryAndItemAreRejected() {
        Order order = newOrder("Karavot", "7000000");
        CategoryRow category = newCategory();
        ItemRow item = catalogService.createItem(category.id(), "Matras-" + System.nanoTime(), "dona",
                new BigDecimal("1000"));

        catalogService.setItemActive(item.id(), false);
        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), category.id(), item.id(),
                null, BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now()));

        catalogService.setItemActive(item.id(), true);
        catalogService.setCategoryActive(category.id(), false);
        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), category.id(), item.id(),
                null, BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now()));
    }

    @Test
    void itemMustBelongToTheChosenCategory() {
        Order order = newOrder("Rack", "6000000");
        CategoryRow catA = newCategory();
        CategoryRow catB = newCategory();
        ItemRow itemA = catalogService.createItem(catA.id(), "Element-" + System.nanoTime(), "dona",
                new BigDecimal("1000"));
        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), catB.id(), itemA.id(),
                null, BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now()));
    }

    @Test
    void oneOffExpenseNeedsDescriptionAndQuantityMustBePositive() {
        Order order = newOrder("Stol2", "4000000");
        CategoryRow category = newCategory();

        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), category.id(), null,
                "   ", BigDecimal.ONE, new BigDecimal("1000"), LocalDate.now()));
        assertThrows(BusinessException.class, () -> expenseService.addExpense(order.getId(), category.id(), null,
                "Transport", BigDecimal.ZERO, new BigDecimal("1000"), LocalDate.now()));

        ExpenseRow ok = expenseService.addExpense(order.getId(), category.id(), null, "Transport", BigDecimal.ONE,
                new BigDecimal("50000"), LocalDate.now());
        assertEquals("Transport", ok.itemName());
    }

    @Test
    void deactivatedItemStillAppearsByNameInOldRows() {
        Order order = newOrder("Javon", "9000000");
        CategoryRow category = newCategory();
        ItemRow item = catalogService.createItem(category.id(), "Shisha-" + System.nanoTime(), "m²",
                new BigDecimal("120000"));
        ExpenseRow row = expenseService.addExpense(order.getId(), category.id(), item.id(), null,
                new BigDecimal("3"), new BigDecimal("120000"), LocalDate.now());

        catalogService.setItemActive(item.id(), false);

        ExpenseRow reloaded = expenseService.listRows(order.getId()).stream()
                .filter(r -> r.id().equals(row.id())).findFirst().orElseThrow();
        assertEquals(item.name(), reloaded.itemName());
    }

    @Test
    void financeProfitBeforeLaborAndPercent() {
        Order order = newOrder("Komplekt", "10000");
        CategoryRow category = newCategory();
        expenseService.addExpense(order.getId(), category.id(), null, "A", BigDecimal.ONE, new BigDecimal("2000"),
                LocalDate.now());
        ExpenseRow drop = expenseService.addExpense(order.getId(), category.id(), null, "B", BigDecimal.ONE,
                new BigDecimal("1500"), LocalDate.now());
        expenseService.voidExpense(drop.id(), "Xato");

        assertEquals(0, orderFinanceService.expensesTotal(order.getId()).compareTo(new BigDecimal("2000")));
        assertEquals(0, orderFinanceService.profitBeforeLabor(order.getId()).compareTo(new BigDecimal("8000")));
        assertEquals(0, orderFinanceService.profitBeforeLaborPercent(order.getId()).compareTo(new BigDecimal("80.0")));

        List<com.mebelsex.orders.ExpenseCategoryTotal> byCategory = orderFinanceService
                .expensesByCategory(order.getId());
        assertEquals(1, byCategory.size());
        assertEquals(0, byCategory.get(0).total().compareTo(new BigDecimal("2000")));
    }

    @Test
    void percentIsNullWhenPriceIsZero() {
        Order order = newOrder("NolNarx", "1000");
        Order stored = orderRepository.findById(order.getId()).orElseThrow();
        stored.setAgreedPrice(BigDecimal.ZERO);
        orderRepository.save(stored);

        assertNull(orderFinanceService.profitBeforeLaborPercent(order.getId()));
    }
}
