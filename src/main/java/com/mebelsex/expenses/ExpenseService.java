package com.mebelsex.expenses;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Order expenses. unit_price and amount are frozen at creation (quantity x unit price). */
@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseItemRepository itemRepository;
    private final OrderRepository orderRepository;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseCategoryRepository categoryRepository,
            ExpenseItemRepository itemRepository, OrderRepository orderRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ExpenseRow addExpense(Long orderId, Long categoryId, Long itemIdOrNull, String description,
            BigDecimal quantity, BigDecimal unitPrice, LocalDate date) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("expense.error.order_cancelled");
        }

        ExpenseCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("expense.category.notfound"));
        if (!category.isActive()) {
            throw new BusinessException("expense.error.category_inactive");
        }

        ExpenseItem item = null;
        if (itemIdOrNull != null) {
            item = itemRepository.findById(itemIdOrNull)
                    .orElseThrow(() -> new BusinessException("expense.item.notfound"));
            if (!item.isActive()) {
                throw new BusinessException("expense.error.item_inactive");
            }
            if (!item.getCategory().getId().equals(category.getId())) {
                throw new BusinessException("expense.error.item_category_mismatch");
            }
        } else if (description == null || description.trim().isEmpty()) {
            throw new BusinessException("expense.error.description_required");
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("expense.error.quantity_invalid");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("expense.error.price_invalid");
        }

        Expense expense = new Expense();
        expense.setOrder(order);
        expense.setCategory(category);
        expense.setItem(item);
        expense.setDescription(description == null ? null : description.trim());
        expense.setQuantity(quantity);
        expense.setUnitPrice(unitPrice);
        expense.setAmount(quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP));
        expense.setExpenseDate(date);

        return toRow(expenseRepository.save(expense));
    }

    @Transactional
    public void voidExpense(Long expenseId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("expense.error.void_reason_required");
        }
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new BusinessException("expense.notfound"));
        if (expense.getStatus() == RecordStatus.VOIDED) {
            throw new BusinessException("expense.error.already_voided");
        }
        expense.setStatus(RecordStatus.VOIDED);
        expense.setVoidReason(reason.trim());
        expenseRepository.save(expense);
    }

    /** Newest first (by date, then id); VOIDED rows are included. */
    public List<ExpenseRow> listRows(Long orderId) {
        return expenseRepository.findByOrderId(orderId).stream()
                .sorted(Comparator
                        .comparing(Expense::getExpenseDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Expense::getId, Comparator.reverseOrder()))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    private ExpenseRow toRow(Expense expense) {
        ExpenseItem item = expense.getItem();
        String itemName = item != null ? item.getName() : expense.getDescription();
        String unit = item != null ? item.getUnit() : null;
        return new ExpenseRow(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getCategory().getName(),
                itemName,
                unit,
                expense.getQuantity(),
                expense.getUnitPrice(),
                expense.getAmount(),
                expense.getStatus(),
                expense.getVoidReason());
    }
}
