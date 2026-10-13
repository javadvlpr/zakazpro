package com.mebelsex.report;

/** A report column: title, cell type (rendering) and alignment. */
public record Column(String title, CellType type, Align align) {

    public static Column of(String title, CellType type, Align align) {
        return new Column(title, type, align);
    }
}
