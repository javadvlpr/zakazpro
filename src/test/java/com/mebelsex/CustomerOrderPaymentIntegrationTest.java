package com.mebelsex;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderFinanceService;
import com.mebelsex.orders.OrderService;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.Payment;
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
                "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
                "spring.datasource.driver-class-name=org.sqlite.JDBC",
                "spring.flyway.enabled=true"
})
class CustomerOrderPaymentIntegrationTest {

        @Autowired
        private CustomerService customerService;

        @Autowired
        private OrderService orderService;

        @Autowired
        private PaymentService paymentService;

        @Autowired
        private OrderFinanceService orderFinanceService;

        @Test
        void testLocalDateRoundTripAndValidatePasses() {
                Customer c = customerService.create("Local Date Test", null, null, null);
                Order o = orderService.create(c.getId(), "Table", new BigDecimal("5000"), LocalDate.of(2026, 12, 31),
                                null,
                                null, null, null);

                Order fetched = orderService.findById(o.getId()).get();
                assertNotNull(fetched.getDueDate());
                assertEquals(2026, fetched.getDueDate().getYear());
                assertEquals(12, fetched.getDueDate().getMonthValue());
                assertEquals(31, fetched.getDueDate().getDayOfMonth());
        }

        @Test
        void testCreateOrderWithAdvanceAndRollback() {
                Customer c = customerService.create("Advance Rollback Test", null, null, null);

                // 1. Valid advance: Both created
                Order o1 = orderService.create(c.getId(), "Valid", new BigDecimal("2000"), LocalDate.now(), null,
                                new BigDecimal("1000"), PaymentMethod.CASH,
                                LocalDate.now());
                assertNotNull(o1.getOrderNumber());
                assertTrue(Integer.parseInt(o1.getOrderNumber()) > 0);
                assertEquals(new BigDecimal("1000"), orderFinanceService.paidByCustomer(o1.getId()));

                // 2. Invalid advance (exceeds debt): Should rollback everything
                Exception ex = assertThrows(BusinessException.class, () -> {
                        orderService.create(c.getId(), "Invalid", new BigDecimal("2000"), LocalDate.now(), null,
                                        new BigDecimal("3000"), PaymentMethod.CASH,
                                        LocalDate.now());
                });
                assertEquals("payment.error.exceeds_debt", ex.getMessage());

                // Check order wasn't created
                List<Order> orders = orderService.list(null, c.getId(), null, null, null);
                assertEquals(1, orders.size(), "Rolled back order should not exist");
        }

        @Test
        void testSequentialOrderNumbers() {
                Customer c = customerService.create("Sequential Test", null, null, null);
                Order o1 = orderService.create(c.getId(), "S1", new BigDecimal("1000"), LocalDate.now(), null, null,
                                null,
                                null);
                Order o2 = orderService.create(c.getId(), "S2", new BigDecimal("1000"), LocalDate.now(), null, null,
                                null,
                                null);
                Order o3 = orderService.create(c.getId(), "S3", new BigDecimal("1000"), LocalDate.now(), null, null,
                                null,
                                null);

                int n1 = Integer.parseInt(o1.getOrderNumber());
                assertEquals(n1 + 1, Integer.parseInt(o2.getOrderNumber()));
                assertEquals(n1 + 2, Integer.parseInt(o3.getOrderNumber()));
        }

        @Test
        void testDebtCalculationsWithVoidedAndRefund() {
                Customer c = customerService.create("Finance Test", null, null, null);
                Order o = orderService.create(c.getId(), "Sofa", new BigDecimal("10000"), LocalDate.now(), null,
                                new BigDecimal("2000"), PaymentMethod.CASH,
                                LocalDate.now());

                // Debt should be 8000
                assertEquals(new BigDecimal("8000"), orderFinanceService.customerDebt(o.getId()));
                assertEquals(new BigDecimal("2000"), orderFinanceService.paidByCustomer(o.getId()));

                // Regular payment 3000
                Payment p1 = paymentService.addPayment(o.getId(), new BigDecimal("3000"), PaymentKind.REGULAR,
                                PaymentMethod.CARD,
                                LocalDate.now(), null);
                assertEquals(new BigDecimal("5000"), orderFinanceService.customerDebt(o.getId()));

                // Refund 1000
                Payment p2 = paymentService.addPayment(o.getId(), new BigDecimal("1000"), PaymentKind.REFUND,
                                PaymentMethod.CASH,
                                LocalDate.now(), null);
                assertEquals(new BigDecimal("6000"), orderFinanceService.customerDebt(o.getId()));
                assertEquals(new BigDecimal("4000"), orderFinanceService.paidByCustomer(o.getId()));

                // Void Regular payment
                paymentService.voidPayment(p1.getId(), "Mistake");
                assertEquals(new BigDecimal("9000"), orderFinanceService.customerDebt(o.getId())); // 10000 - 2000(adv)
                                                                                                   // +
                                                                                                   // 1000(ref)
                assertEquals(new BigDecimal("1000"), orderFinanceService.paidByCustomer(o.getId()));
        }

        @Test
        void testOverpaymentAndRefundGuards() {
                Customer c = customerService.create("Guard Test", null, null, null);
                Order o = orderService.create(c.getId(), "Chair", new BigDecimal("5000"), LocalDate.now(), null,
                                new BigDecimal("1000"), PaymentMethod.CASH,
                                LocalDate.now());

                // Overpayment rejected
                Exception overpayEx = assertThrows(BusinessException.class, () -> {
                        paymentService.addPayment(o.getId(), new BigDecimal("5000"), PaymentKind.REGULAR,
                                        PaymentMethod.CASH,
                                        LocalDate.now(), null);
                });
                assertEquals("payment.error.exceeds_debt", overpayEx.getMessage());

                // Refund over paid rejected
                Exception overrefundEx = assertThrows(BusinessException.class, () -> {
                        paymentService.addPayment(o.getId(), new BigDecimal("2000"), PaymentKind.REFUND,
                                        PaymentMethod.CASH,
                                        LocalDate.now(), null);
                });
                assertEquals("payment.error.exceeds_paid", overrefundEx.getMessage());

                // Payment to CANCELLED rejected
                orderService.changeStatus(o.getId(), OrderStatus.CANCELLED);
                Exception cancelEx = assertThrows(BusinessException.class, () -> {
                        paymentService.addPayment(o.getId(), new BigDecimal("1000"), PaymentKind.REGULAR,
                                        PaymentMethod.CASH,
                                        LocalDate.now(), null);
                });
                assertEquals("payment.error.order_cancelled", cancelEx.getMessage());
        }

        @Test
        void testLoweringPriceBelowPaidRejected() {
                Customer c = customerService.create("Price Test", null, null, null);
                Order o = orderService.create(c.getId(), "Bed", new BigDecimal("5000"), LocalDate.now(), null,
                                new BigDecimal("3000"), PaymentMethod.CASH,
                                LocalDate.now());

                Exception ex = assertThrows(BusinessException.class, () -> {
                        orderService.update(o.getId(), new BigDecimal("2000"), LocalDate.now(), "Bed Updated", null);
                });
                assertEquals("order.error.price.below_paid", ex.getMessage());

                // Valid update
                orderService.update(o.getId(), new BigDecimal("3000"), LocalDate.now(), "Bed Updated", null);
                Order updated = orderService.findById(o.getId()).get();
                assertEquals(new BigDecimal("3000"), updated.getAgreedPrice());
        }

        @Test
        void testCustomerWithOrdersCannotBeDeleted() {
                Customer c = customerService.create("Delete Test", null, null, null);
                orderService.create(c.getId(), "Testing Delete", new BigDecimal("1000"), LocalDate.now(), null, null,
                                null,
                                null);

                Exception ex = assertThrows(BusinessException.class, () -> {
                        customerService.delete(c.getId());
                });
                assertEquals("customer.error.has.orders", ex.getMessage());

                // But clean customer CAN be deleted
                Customer clean = customerService.create("Clean Client", null, null, null);
                assertDoesNotThrow(() -> customerService.delete(clean.getId()));
        }

        @Test
        void testCustomerSearch() {
                Customer c1 = customerService.create("Aziz Azizov", "+998901234567", "Tashkent", null);
                Customer c2 = customerService.create("Botir Botirov", "+998909876543", "Samarkand", null);

                List<Customer> azizs = customerService.search("Aziz");
                assertEquals(1, azizs.size());
                assertEquals("Aziz Azizov", azizs.get(0).getFullName());

                List<Customer> byPhone = customerService.search("9876543");
                assertEquals(1, byPhone.size());
                assertEquals("Botir Botirov", byPhone.get(0).getFullName());
        }

        @Test
        void testOrderFilters() {
                Customer c = customerService.create("Filter Test Client", null, null, null);
                orderService.create(c.getId(), "Table Blue", new BigDecimal("2000"), LocalDate.of(2026, 10, 10), null,
                                null, null, null);
                orderService.create(c.getId(), "Chair Red", new BigDecimal("1000"), LocalDate.of(2026, 11, 10), null,
                                null, null, null);

                List<Order> onlyBlue = orderService.list(null, null, "Blue", null, null);
                assertEquals(1, onlyBlue.size());
                assertEquals("Table Blue", onlyBlue.get(0).getProductDescription());

                List<Order> byDate = orderService.list(null, null, null, LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 12, 1));
                assertEquals(1, byDate.size());
                assertEquals("Chair Red", byDate.get(0).getProductDescription());
        }

        @Test
        void testVoidPaymentRequiresReason() {
                Customer c = customerService.create("Void Reason Test", null, null, null);
                Order o = orderService.create(c.getId(), "Shelf", new BigDecimal("1000"), LocalDate.now(), null, null,
                                null, null);
                Payment p = paymentService.addPayment(o.getId(), new BigDecimal("1000"), PaymentKind.REGULAR,
                                PaymentMethod.CASH, LocalDate.now(), "cash check");

                Exception ex = assertThrows(BusinessException.class, () -> {
                        paymentService.voidPayment(p.getId(), "  ");
                });
                assertEquals("payment.error.void_reason_required", ex.getMessage());

                assertDoesNotThrow(() -> paymentService.voidPayment(p.getId(), "Customer changed mind"));
        }

        @Autowired
        private org.springframework.context.ApplicationContext context;

        @Test
        void testDdlValidateWorks() {
                // Since we reach this test, the Spring Context loaded.
                // Which means Hibernate ddl-auto=validate succeeded without throwing
                // exceptions,
                // proving schema types and mapped types (TimestampConverter for text) match
                // perfectly.
                assertNotNull(context);
        }
}
