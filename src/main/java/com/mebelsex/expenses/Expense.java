package com.mebelsex.expenses;

import com.mebelsex.common.BaseEntity;
import com.mebelsex.common.LocalDateConverter;
import com.mebelsex.common.MoneyConverter;
import com.mebelsex.common.QuantityConverter;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.orders.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Order expense. unit_price and amount are frozen at creation time.
 * order is optional (general costs later); the UI always sets it.
 */
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_item_id")
    private ExpenseItem item;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = QuantityConverter.class)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = LocalDateConverter.class)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "void_reason")
    private String voidReason;

    public Expense() {
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public ExpenseItem getItem() {
        return item;
    }

    public void setItem(ExpenseItem item) {
        this.item = item;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }
}
