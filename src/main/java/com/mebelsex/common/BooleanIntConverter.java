package com.mebelsex.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts Boolean (Java) ↔ Integer 0/1 (DB INTEGER columns, e.g. `active`).
 * V1 declares these columns as INTEGER, so Hibernate must see an integer type.
 */
@Converter
public class BooleanIntConverter implements AttributeConverter<Boolean, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? 1 : 0;
    }

    @Override
    public Boolean convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData != 0;
    }
}
