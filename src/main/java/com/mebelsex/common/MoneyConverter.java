package com.mebelsex.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts BigDecimal (Java) ↔ Long (DB column INTEGER / BIGINT semantics).
 * Scale 0, RoundingMode.HALF_UP. Null-safe.
 */
@Converter
public class MoneyConverter implements AttributeConverter<BigDecimal, Long> {

    @Override
    public Long convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    public BigDecimal convertToEntityAttribute(Long dbData) {
        if (dbData == null) {
            return null;
        }
        return BigDecimal.valueOf(dbData);
    }
}
