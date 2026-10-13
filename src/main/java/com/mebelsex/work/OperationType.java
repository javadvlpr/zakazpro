package com.mebelsex.work;

import com.mebelsex.common.BaseEntity;
import com.mebelsex.common.BooleanIntConverter;
import com.mebelsex.common.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Operation type / work kind (operation_types) with a suggestion-only default price. */
@Entity
@Table(name = "operation_types")
public class OperationType extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "unit")
    private String unit;

    @Column(name = "default_price", nullable = false, columnDefinition = "INTEGER")
    @Convert(converter = MoneyConverter.class)
    private BigDecimal defaultPrice = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Convert(converter = BooleanIntConverter.class)
    private boolean active = true;

    public OperationType() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getDefaultPrice() {
        return defaultPrice;
    }

    public void setDefaultPrice(BigDecimal defaultPrice) {
        this.defaultPrice = defaultPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
