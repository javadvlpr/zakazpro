package com.mebelsex.orders;

import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.payments.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-orderlist-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class OrderListIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Test
    public void testOrderListMetrics() {
        Customer c = customerService.create("Metrics Test", null, null, null);

        // Order 1: Overdue, normal debt calculations
        Order o1 = orderService.create(c.getId(), "Desk", new BigDecimal("10000"), LocalDate.now().minusDays(1), null,
                new BigDecimal("2000"), PaymentMethod.CASH, LocalDate.now());

        // Add regular payment
        paymentService.addPayment(o1.getId(), new BigDecimal("3000"), PaymentKind.REGULAR, PaymentMethod.CARD,
                LocalDate.now(), null);

        // Add refund
        paymentService.addPayment(o1.getId(), new BigDecimal("500"), PaymentKind.REFUND, PaymentMethod.CASH,
                LocalDate.now(), null);

        // Add a voided payment
        var pVoid = paymentService.addPayment(o1.getId(), new BigDecimal("4000"), PaymentKind.REGULAR,
                PaymentMethod.CASH, LocalDate.now(), null);
        paymentService.voidPayment(pVoid.getId(), "Error");

        List<OrderListItem> items = orderService.listItems(null, true, null, null);
        OrderListItem row1 = items.stream().filter(r -> r.id().equals(o1.getId())).findFirst().get();

        // 2000 + 3000 - 500 = 4500 paid. Debt = 10000 - 4500 = 5500
        assertEquals(new BigDecimal("4500"), row1.paid());
        assertEquals(new BigDecimal("5500"), row1.debt());
        assertTrue(row1.overdue(), "Should be overdue since duedate is in past and status=NEW");

        // Order 2: Cancelled order rule checks
        Order o2 = orderService.create(c.getId(), "Sofa", new BigDecimal("5000"), LocalDate.now().plusDays(1), null,
                new BigDecimal("1000"), PaymentMethod.CASH, LocalDate.now());
        orderService.changeStatus(o2.getId(), OrderStatus.CANCELLED);

        items = orderService.listItems(null, true, null, null);
        OrderListItem row2 = items.stream().filter(r -> r.id().equals(o2.getId())).findFirst().get();
        assertEquals(new BigDecimal("1000"), row2.paid(), "Paid is still preserved");
        assertEquals(BigDecimal.ZERO, row2.debt(), "Debt is 0 for cancelled orders");
        assertFalse(row2.overdue(), "Cancelled orders are never overdue");

        // Include closed filter test
        List<OrderListItem> openItems = orderService.listItems(null, false, null, null);
        assertTrue(openItems.stream().noneMatch(r -> r.id().equals(o2.getId())),
                "Include closed = false must hide cancelled orders");
    }

    @Test
    public void testNumericSortingByOrderNumber() {
        Customer c = customerService.create("Sort Test", null, null, null);

        // Dates are null or same to rely on second order sorting
        Order o_10 = orderService.create(c.getId(), "T10", new BigDecimal("100"), null, null, null, null, null);
        Order o_2 = orderService.create(c.getId(), "T2", new BigDecimal("100"), null, null, null, null, null);

        // Hack the numbers to simulate strings that need numeric sort "10" vs "2"
        // Wait, sequential numbers are assigned automatically by max+1.
        // I will just read all of them. The latest ones have larger IDs/Order numbers.
        // Let me just verify they sort correctly where "2" comes BEFORE "10" even
        // though "10" < "2" as strings.
        // Let's create enough orders.
        for (int i = 0; i < 15; i++) {
            orderService.create(c.getId(), "Tx", new BigDecimal("100"), null, null, null, null, null);
        }

        List<OrderListItem> result = orderService.listItems(null, true, null, null);
        // Ensure "2" comes before "10"
        int idx2 = -1;
        int idx10 = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).orderNumber().equals("2"))
                idx2 = i;
            if (result.get(i).orderNumber().equals("10"))
                idx10 = i;
        }
        assertTrue(idx2 != -1 && idx10 != -1 && idx2 < idx10, "Order 2 must appear before Order 10");
    }
}
