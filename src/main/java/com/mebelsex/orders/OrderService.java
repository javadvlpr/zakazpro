package com.mebelsex.orders;

import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerRepository;
import com.mebelsex.payments.Payment;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.payments.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final OrderFinanceService orderFinanceService;

    public OrderService(OrderRepository orderRepository, CustomerRepository customerRepository,
            PaymentRepository paymentRepository, OrderFinanceService orderFinanceService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.orderFinanceService = orderFinanceService;
    }

    @Transactional
    public Order create(Long customerId, String productDescription, BigDecimal agreedPrice, LocalDate dueDate,
            String notes,
            BigDecimal advanceAmount, PaymentMethod advanceMethod, LocalDate advanceDate) {
        return create(customerId, productDescription, agreedPrice, dueDate, LocalDate.now(), notes,
                advanceAmount, advanceMethod, advanceDate);
    }

    @Transactional
    public Order create(Long customerId, String productDescription, BigDecimal agreedPrice, LocalDate dueDate,
            LocalDate orderDate, String notes,
            BigDecimal advanceAmount, PaymentMethod advanceMethod, LocalDate advanceDate) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("customer.error.notfound"));

        if (agreedPrice == null || agreedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order.error.price.invalid");
        }
        if (orderDate == null) {
            throw new BusinessException("order.error.date_required");
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setProductDescription(productDescription);
        order.setAgreedPrice(agreedPrice);
        order.setDueDate(dueDate);
        order.setOrderDate(orderDate);
        order.setNotes(notes);
        order.setStatus(OrderStatus.NEW);

        // Generate sequential order number
        Integer maxNum = orderRepository.findMaxOrderNumber();
        int nextNum = (maxNum != null) ? maxNum + 1 : 1;
        order.setOrderNumber(String.valueOf(nextNum));

        order = orderRepository.save(order);

        // Optional advance
        if (advanceAmount != null && advanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (advanceMethod == null || advanceDate == null) {
                throw new BusinessException("order.error.advance.incomplete");
            }
            if (advanceAmount.compareTo(agreedPrice) > 0) {
                throw new BusinessException("payment.error.exceeds_debt");
            }
            java.util.ResourceBundle bundle = com.mebelsex.common.Messages.bundle();
            Payment advance = new Payment(order, advanceAmount, PaymentKind.ADVANCE, advanceMethod, advanceDate,
                    bundle.getString("payment.advance.comment"));
            paymentRepository.save(advance);
        }

        return order;
    }

    @Transactional
    public Order update(Long orderId, BigDecimal agreedPrice, LocalDate dueDate, String productDescription,
            String notes) {
        return update(orderId, agreedPrice, dueDate, null, productDescription, notes);
    }

    @Transactional
    public Order update(Long orderId, BigDecimal agreedPrice, LocalDate dueDate, LocalDate orderDate,
            String productDescription, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));

        if (agreedPrice == null || agreedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order.error.price.invalid");
        }

        BigDecimal paid = orderFinanceService.paidByCustomer(orderId);
        if (agreedPrice.compareTo(paid) < 0) {
            throw new BusinessException("order.error.price.below_paid");
        }

        order.setAgreedPrice(agreedPrice);
        order.setDueDate(dueDate);
        if (orderDate != null) {
            order.setOrderDate(orderDate);
        }
        order.setProductDescription(productDescription);
        order.setNotes(notes);

        return orderRepository.save(order);
    }

    @Transactional
    public Order changeStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public OrderCardDto getCard(Long orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));

        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        BigDecimal paid = orderFinanceService.calculatePaid(payments);
        BigDecimal debt = orderFinanceService.calculateDebt(o.getAgreedPrice(), paid);
        if (o.getStatus() == OrderStatus.CANCELLED) {
            debt = BigDecimal.ZERO;
        }

        LocalDate today = LocalDate.now();
        boolean overdue = o.getDueDate() != null && o.getDueDate().isBefore(today)
                && o.getStatus() != OrderStatus.DELIVERED
                && o.getStatus() != OrderStatus.COMPLETED
                && o.getStatus() != OrderStatus.CANCELLED;

        Customer customer = o.getCustomer();
        return new OrderCardDto(
                o.getId(),
                o.getOrderNumber(),
                customer.getFullName(),
                customer.getPhone(),
                o.getProductDescription(),
                o.getAgreedPrice(),
                o.getOrderDate(),
                o.getDueDate(),
                o.getStatus(),
                paid,
                debt,
                overdue,
                orderFinanceService.expensesTotal(orderId),
                orderFinanceService.profitBeforeLabor(orderId),
                orderFinanceService.profitBeforeLaborPercent(orderId),
                orderFinanceService.expensesByCategory(orderId),
                orderFinanceService.laborTotal(orderId),
                orderFinanceService.laborByEmployee(orderId),
                orderFinanceService.netProfit(orderId),
                orderFinanceService.netProfitPercent(orderId));
    }

    @Transactional(readOnly = true)
    public OrderEditDto getForEdit(Long orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));

        Customer customer = o.getCustomer();
        BigDecimal paid = orderFinanceService.paidByCustomer(orderId);

        return new OrderEditDto(
                o.getId(),
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                o.getProductDescription(),
                o.getAgreedPrice(),
                o.getOrderDate(),
                o.getDueDate(),
                o.getNotes(),
                paid);
    }

    public List<Order> list(OrderStatus status, Long customerId, String query, LocalDate startDate, LocalDate endDate) {
        return orderRepository.searchOrders(status, customerId, query, startDate, endDate);
    }

    public List<OrderListItem> listItems(OrderStatus statusFilter, boolean includeClosed, String text,
            Long customerId) {
        // Query orders with search string, status filter, and customer logic
        List<Order> orders = orderRepository.searchOrders(statusFilter, customerId, text, null, null);

        // Filter closed orders at memory level (or could add to custom repo logic, but
        // this preserves existing method constraints)
        if (!includeClosed) {
            orders = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                    .collect(Collectors.toList());
        }

        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        List<Payment> payments = paymentRepository.findByOrderIdIn(orderIds);
        Map<Long, List<Payment>> paymentsByOrderId = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getOrder().getId()));

        LocalDate today = LocalDate.now();
        List<OrderListItem> items = new ArrayList<>();

        for (Order o : orders) {
            List<Payment> orderPayments = paymentsByOrderId.getOrDefault(o.getId(), Collections.emptyList());
            BigDecimal paid = orderFinanceService.calculatePaid(orderPayments);
            BigDecimal debt = orderFinanceService.calculateDebt(o.getAgreedPrice(), paid);

            // Cancelled orders shouldn't accumulate real 'debt' relative to outstanding
            // balances, but prompt states:
            // "Debt/paid use the same ACTIVE-payments formulas as OrderFinanceService".
            // OrderFinanceService is blind to cancelled context unless inside
            // findOrdersWithDebt.
            // However, it explicitly mentions "Debt/paid use the same ACTIVE-payments
            // formulas... cancelled order excluded from debt totals."
            // Wait, "Debt/paid use the same ACTIVE-payments formulas... cancelled order
            // excluded from debt totals."
            if (o.getStatus() == OrderStatus.CANCELLED) {
                debt = BigDecimal.ZERO;
            }

            boolean overdue = o.getDueDate() != null && o.getDueDate().isBefore(today)
                    && o.getStatus() != OrderStatus.DELIVERED
                    && o.getStatus() != OrderStatus.COMPLETED
                    && o.getStatus() != OrderStatus.CANCELLED;

            items.add(new OrderListItem(
                    o.getId(),
                    o.getOrderNumber(),
                    o.getCustomer().getFullName(),
                    o.getProductDescription(),
                    o.getAgreedPrice(),
                    paid,
                    debt,
                    o.getOrderDate(),
                    o.getDueDate(),
                    o.getStatus(),
                    overdue));
        }

        // Sort by dueDate asc (nulls last), then numeric order number.
        items.sort(Comparator
                .<OrderListItem, LocalDate>comparing(OrderListItem::dueDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> {
                    try {
                        return Integer.parseInt(item.orderNumber());
                    } catch (NumberFormatException e) {
                        return 999999;
                    }
                }));

        return items;
    }
}
