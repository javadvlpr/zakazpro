package com.mebelsex.settings;

import com.mebelsex.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Key-value settings entity mapped to the 'settings' table.
 */
@Entity
@Table(name = "settings")
public class Setting extends BaseEntity {

    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(name = "value")
    private String value;

    public Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
