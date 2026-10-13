package com.mebelsex.report;

/**
 * A single report cell. The raw value is a String, Long, BigDecimal or LocalDate.
 * A null value is rendered as "—".
 */
public record Cell(Object value) {

    public static Cell of(Object value) {
        return new Cell(value);
    }

    public static Cell empty() {
        return new Cell(null);
    }

    public boolean isEmpty() {
        return value == null;
    }
}
