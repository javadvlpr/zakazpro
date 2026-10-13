package com.mebelsex.report;

import java.util.List;

/**
 * A report row. {@code orderId} (nullable) links the row to an order card;
 * {@code subtotal} marks subtotal / totals rows for bold rendering.
 */
public record Row(List<Cell> cells, Long orderId, boolean subtotal) {

    public static Row data(List<Cell> cells, Long orderId) {
        return new Row(cells, orderId, false);
    }

    public static Row plain(List<Cell> cells) {
        return new Row(cells, null, false);
    }

    public static Row subtotal(List<Cell> cells) {
        return new Row(cells, null, true);
    }
}
