package com.mebelsex.orders;

import com.mebelsex.common.RecordStatus;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.payments.PaymentRow;
import com.mebelsex.payments.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-card-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class OrderCardDashboardIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private DashboardService dashboardService;

    @Test
    void getCardComputesPaidAndDebtIgnoringVoided() {
        Customer c = customerService.create("Card Client", "+998901112233", null, null);
        Order o = orderService.create(c.getId(), "Desk", new BigDecimal("10000"), LocalDate.now().plusDays(5), null,
                new BigDecimal("2000"), PaymentMethod.CASH, LocalDate.now());

        paymentService.addPayment(o.getId(), new BigDecimal("3000"), PaymentKind.REGULAR, PaymentMethod.CARD,
                LocalDate.now(), null);
        paymentService.addPayment(o.getId(), new BigDecimal("500"), PaymentKind.REFUND, PaymentMethod.CASH,
                LocalDate.now(), null);
        Payment voided = paymentService.addPayment(o.getId(), new BigDecimal("4000"), PaymentKind.REGULAR,
                PaymentMethod.CASH, LocalDate.now(), null);
        paymentService.voidPayment(voided.getId(), "Mistake");

        OrderCardDto card = orderService.getCard(o.getId());

        assertEquals(new BigDecimal("4500"), card.paid(), "2000 + 3000 - 500; voided ignored");
        assertEquals(new BigDecimal("5500"), card.debt(), "10000 - 4500");
        assertEquals("Card Client", card.customerName());
        assertEquals("+998901112233", card.customerPhone());
        assertEquals("Desk", card.productDescription());
        assertEquals(OrderStatus.NEW, card.status());
        assertFalse(card.overdue(), "Due date is in the future");
    }

    @Test
    void listRowsIncludesVoidedNewestFirst() {
        Customer c = customerService.create("Rows Client", null, null, null);
        Order o = orderService.create(c.getId(), "Chair", new BigDecimal("100000"), LocalDate.now().plusDays(5), null,
                null, null, null);

        Payment old = paymentService.addPayment(o.getId(), new BigDecimal("1000"), PaymentKind.REGULAR,
                PaymentMethod.CASH, LocalDate.now().minusDays(2), "old");
        Payment newer = paymentService.addPayment(o.getId(), new BigDecimal("500"), PaymentKind.REGULAR,
                PaymentMethod.CARD, LocalDate.now(), "new");
        Payment voided = paymentService.addPayment(o.getId(), new BigDecimal("200"), PaymentKind.REGULAR,
                PaymentMethod.CASH, LocalDate.now(), "voided row");
        paymentService.voidPayment(voided.getId(), "Mistake");

        List<PaymentRow> rows = paymentService.listRows(o.getId());

        assertEquals(3, rows.size(), "VOIDED rows are included");
        assertEquals(voided.getId(), rows.get(0).id(), "Same date, higher id first");
        assertEquals(newer.getId(), rows.get(1).id());
        assertEquals(old.getId(), rows.get(2).id());

        PaymentRow voidedRow = rows.stream().filter(r -> r.id().equals(voided.getId())).findFirst().orElseThrow();
        assertEquals(RecordStatus.VOIDED, voidedRow.status());
        assertEquals("Mistake", voidedRow.voidReason());
    }

    @Test
    void summaryComputesCountsDebtIncomeAndDueSoon() {
        LocalDate today = LocalDate.now();
        DashboardSummary before = dashboardService.getSummary(today);

        Customer c = customerService.create("Dash Client", null, null, null);

        // 1) active, overdue, with a regular payment and a refund this month
        Order active = orderService.create(c.getId(), "Active Desk", new BigDecimal("1000"), today.minusDays(1), null,
                null, null, null);
        paymentService.addPayment(active.getId(), new BigDecimal("200"), PaymentKind.REGULAR, PaymentMethod.CASH, today,
                null);
        paymentService.addPayment(active.getId(), new BigDecimal("50"), PaymentKind.REFUND, PaymentMethod.CASH, today,
                null);

        // 2) completed: excluded from active count, but its remaining debt still counts
        Order completed = orderService.create(c.getId(), "Completed Sofa", new BigDecimal("500"), today.plusDays(3),
                null, null, null, null);
        orderService.changeStatus(completed.getId(), OrderStatus.COMPLETED);

        // 3) cancelled with an advance: excluded from active count and total debt
        Order cancelled = orderService.create(c.getId(), "Cancelled Bed", new BigDecimal("700"), today.plusDays(2),
                null, new BigDecimal("100"), PaymentMethod.CASH, today);
        orderService.changeStatus(cancelled.getId(), OrderStatus.CANCELLED);

        // 4) due soon (in window)
        Order soon = orderService.create(c.getId(), "Soon Table", new BigDecimal("300"), today.plusDays(3), null, null,
                null, null);        // 5) far in the future: not due soon
        Order far = orderService.create(c.getId(), "Far Chair", new BigDecimal("100"), today.plusDays(30), null, null,
                null, null);

        DashboardSummary after = dashboardService.getSummary(today);

        // active = not COMPLETED/CANCELLED: active, soon, far -> +3
        assertEquals(before.activeOrders() + 3, after.activeOrders());
        // total debt = non-CANCELLED: active 850 + completed 500 + soon 300 + far 100 = 1750
        assertEquals(before.totalDebt().add(new BigDecimal("1750")), after.totalDebt());
        // overdue: only 'active'
        assertEquals(before.overdueCount() + 1, after.overdueCount());
        // month income = (200 - 50) + 100 = 250
        assertEquals(before.monthIncome().add(new BigDecimal("250")), after.monthIncome());

        List<OrderListItem> dueSoon = after.dueSoon();
        assertTrue(dueSoon.stream().anyMatch(r -> r.id().equals(active.getId())), "Overdue order is due soon");
        assertTrue(dueSoon.stream().anyMatch(r -> r.id().equals(soon.getId())), "Order due in 3 days is due soon");
        assertFalse(dueSoon.stream().anyMatch(r -> r.id().equals(completed.getId())), "Completed is not due soon");
        assertFalse(dueSoon.stream().anyMatch(r -> r.id().equals(cancelled.getId())), "Cancelled is not due soon");
        assertFalse(dueSoon.stream().anyMatch(r -> r.id().equals(far.getId())), "Order due in 30 days is not due soon");

        int idxActive = indexOf(dueSoon, active.getId());
        int idxSoon = indexOf(dueSoon, soon.getId());
        assertTrue(idxActive < idxSoon, "dueSoon is sorted by due date ascending");
    }

    private static int indexOf(List<OrderListItem> items, Long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
