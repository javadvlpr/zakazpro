package com.mebelsex.employees;

import com.mebelsex.common.BaseEntity;
import com.mebelsex.common.BooleanIntConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Employee position (positions). Only deactivated, never deleted. */
@Entity
@Table(name = "positions")
public class Position extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    @Convert(converter = BooleanIntConverter.class)
    private boolean active = true;

    public Position() {
    }

    public Position(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
