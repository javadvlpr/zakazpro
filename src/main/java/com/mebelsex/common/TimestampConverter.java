package com.mebelsex.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converts LocalDateTime ↔ String (DB TEXT)
 * Handle both ISO strict ('T') and SQLite default datetime (' ').
 */
@Converter
public class TimestampConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter SQLITE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        // Save as strict ISO-8601 (with 'T') per AGENTS.md
        return attribute.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            if (dbData.contains("T")) {
                return LocalDateTime.parse(dbData, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else {
                return LocalDateTime.parse(dbData, SQLITE_FMT);
            }
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Cannot parse timestamp: " + dbData, e);
        }
    }
}
