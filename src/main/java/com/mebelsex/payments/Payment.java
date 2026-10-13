package com.mebelsex.payments;

import com.mebelsex.common.BaseEntity;
import com.mebelsex.common.LocalDateConverter;
import com.mebelsex.common.MoneyConverter;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.orders.Order;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "amount", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private PaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Column(name = "payment_date", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = LocalDateConverter.class)
    private LocalDate paymentDate;

    @Column(name = "comment")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "void_reason")
    private String voidReason;

    public Payment() {
    }

    public Payment(Order order, BigDecimal amount, PaymentKind kind, PaymentMethod method, LocalDate paymentDate,
            String comment) {
        this.order = order;
        this.amount = amount;
        this.kind = kind;
        this.method = method;
        this.paymentDate = paymentDate;
        this.comment = comment;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public PaymentKind getKind() {
        return kind;
    }

    public void setKind(PaymentKind kind) {
        this.kind = kind;
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
