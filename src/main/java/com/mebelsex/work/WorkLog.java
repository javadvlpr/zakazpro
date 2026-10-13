package com.mebelsex.work;

import com.mebelsex.common.BaseEntity;
import com.mebelsex.common.LocalDateConverter;
import com.mebelsex.common.MoneyConverter;
import com.mebelsex.common.QuantityConverter;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.Employee;
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
 * A piece of work done on an order (work_logs). unit_price and amount are frozen at creation
 * (amount = quantity x unit_price, scale 0 HALF_UP); later catalog price changes never alter them.
 */
@Entity
@Table(name = "work_logs")
public class WorkLog extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_type_id", nullable = false)
    private OperationType operationType;

    @Column(name = "work_date", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = LocalDateConverter.class)
    private LocalDate workDate;

    @Column(name = "quantity", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = QuantityConverter.class)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal amount;

    @Column(name = "comment")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "void_reason")
    private String voidReason;

    public WorkLog() {
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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
