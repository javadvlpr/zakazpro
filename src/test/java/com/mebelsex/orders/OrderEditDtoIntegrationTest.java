package com.mebelsex.orders;

import com.mebelsex.common.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-edit-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
public class OrderEditDtoIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;

    @Test
    void getForEditReturnsFullyInitializedDataAfterTransactionEnds() {
        Customer c = customerService.create("Edit Client", "+998901234567", "Toshkent", null);
        Order o = orderService.create(c.getId(), "Stol", new BigDecimal("10000"), LocalDate.now().plusDays(4), "Izoh",
                new BigDecimal("2000"), PaymentMethod.CASH, LocalDate.now());
        paymentService.addPayment(o.getId(), new BigDecimal("3000"), PaymentKind.REGULAR, PaymentMethod.CARD,
                LocalDate.now(), null);
        paymentService.addPayment(o.getId(), new BigDecimal("500"), PaymentKind.REFUND, PaymentMethod.CASH,
                LocalDate.now(), null);

        // Transaction is already closed when getForEdit returns; reading the DTO must be safe.
        OrderEditDto dto = orderService.getForEdit(o.getId());

        assertEquals(o.getId(), dto.id());
        assertEquals(c.getId(), dto.customerId());
        assertEquals("Edit Client", dto.customerName());
        assertEquals("+998901234567", dto.customerPhone());
        assertEquals("Stol", dto.productDescription());
        assertEquals(new BigDecimal("10000"), dto.agreedPrice());
        assertEquals(LocalDate.now().plusDays(4), dto.dueDate());
        assertEquals("Izoh", dto.notes());
        assertEquals(new BigDecimal("4500"), dto.paid(), "2000 advance + 3000 regular - 500 refund");
    }

    @Test
    void getForEditMissingOrderThrowsBusinessException() {
        assertThrows(BusinessException.class, () -> orderService.getForEdit(999999L));
    }
}
