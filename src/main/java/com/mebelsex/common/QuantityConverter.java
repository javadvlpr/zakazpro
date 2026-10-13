package com.mebelsex.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/**
 * Converts BigDecimal (Java) ↔ String (DB column TEXT).
 * Quantity may be fractional (e.g. 15.5 m²). Null-safe.
 */
@Converter
public class QuantityConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return new BigDecimal(dbData);
    }
}
