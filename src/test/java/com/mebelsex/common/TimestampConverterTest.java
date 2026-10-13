package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimestampConverterTest {

    private final TimestampConverter converter = new TimestampConverter();

    @Test
    void convertToDatabaseColumn_typical() {
        LocalDateTime dt = LocalDateTime.of(2026, 10, 3, 16, 30, 45);
        assertEquals("2026-10-03T16:30:45", converter.convertToDatabaseColumn(dt));
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToEntityAttribute_sqliteFormat() {
        // "yyyy-MM-dd HH:mm:ss" - typical from SQLite's datetime('now')
        LocalDateTime dt = converter.convertToEntityAttribute("2026-10-03 16:30:45");
        assertNotNull(dt);
        assertEquals(2026, dt.getYear());
        assertEquals(10, dt.getMonthValue());
        assertEquals(3, dt.getDayOfMonth());
        assertEquals(16, dt.getHour());
        assertEquals(30, dt.getMinute());
        assertEquals(45, dt.getSecond());
    }

    @Test
    void convertToEntityAttribute_isoFormat() {
        // "yyyy-MM-ddTHH:mm:ss" - typical ISO from Java
        LocalDateTime dt = converter.convertToEntityAttribute("2026-10-03T16:30:45");
        assertNotNull(dt);
        assertEquals(2026, dt.getYear());
        assertEquals(10, dt.getMonthValue());
        assertEquals(3, dt.getDayOfMonth());
        assertEquals(16, dt.getHour());
        assertEquals(30, dt.getMinute());
        assertEquals(45, dt.getSecond());
    }

    @Test
    void convertToEntityAttribute_null() {
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("   "));
    }
}
