package com.mebelsex.orders;

import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-orderdate-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class OrderDateIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;

    @Test
    void createWithExplicitOrderDateKeepsIt() {
        Customer c = customerService.create("Sana Mijozi", null, null, null);
        LocalDate orderDate = LocalDate.of(2026, 3, 15);

        Order o = orderService.create(c.getId(), "Stol", new BigDecimal("10000"), LocalDate.now().plusDays(5),
                orderDate, null, null, null, null);

        assertEquals(orderDate, o.getOrderDate());
        assertEquals(orderDate, orderService.getCard(o.getId()).orderDate());
        assertEquals(orderDate, orderService.getForEdit(o.getId()).orderDate());
        assertEquals(orderDate,
                orderService.listItems(null, true, null, null).stream()
                        .filter(r -> r.id().equals(o.getId())).findFirst().orElseThrow().orderDate());
    }

    @Test
    void createWithoutExplicitOrderDateDefaultsToToday() {
        Customer c = customerService.create("Bugungi Mijozi", null, null, null);
        Order o = orderService.create(c.getId(), "Divan", new BigDecimal("5000"), LocalDate.now().plusDays(1),
                null, null, null, null);
        assertEquals(LocalDate.now(), o.getOrderDate());
    }

    @Test
    void updateCanChangeOrderDate() {
        Customer c = customerService.create("Tahrir Mijozi", null, null, null);
        Order o = orderService.create(c.getId(), "Shkaf", new BigDecimal("7000"), LocalDate.now().plusDays(1),
                LocalDate.of(2026, 1, 1), null, null, null, null);

        orderService.update(o.getId(), new BigDecimal("7000"), LocalDate.now().plusDays(2),
                LocalDate.of(2026, 2, 2), "Shkaf", null);

        assertEquals(LocalDate.of(2026, 2, 2), orderService.getForEdit(o.getId()).orderDate());
    }
}
