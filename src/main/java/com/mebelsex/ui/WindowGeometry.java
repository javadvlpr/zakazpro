package com.mebelsex.ui;

import java.util.List;

/**
 * Pure window geometry logic (no JavaFX toolkit). The caller passes the connected screen
 * rectangles, so the clamping can be unit-tested.
 */
public final class WindowGeometry {

    public static final double DEFAULT_WIDTH = 1280;
    public static final double DEFAULT_HEIGHT = 800;

    public record Rect(double x, double y, double width, double height) {
        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean intersects(Rect other) {
            return x < other.maxX() && maxX() > other.x && y < other.maxY() && maxY() > other.y;
        }
    }

    private WindowGeometry() {
    }

    /**
     * Resolves the rectangle to use. A saved rectangle that does not intersect any connected
     * screen (or is missing / degenerate) falls back to the default size centred on the
     * primary screen. Otherwise the size is raised to the minimum and the position clamped
     * fully inside the intersecting screen.
     */
    public static Rect resolve(Rect saved, List<Rect> screens, double minWidth, double minHeight) {
        Rect primary = screens == null || screens.isEmpty() ? new Rect(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT)
                : screens.get(0);
        double defaultW = Math.max(DEFAULT_WIDTH, minWidth);
        double defaultH = Math.max(DEFAULT_HEIGHT, minHeight);
        if (saved == null || saved.width() <= 0 || saved.height() <= 0) {
            return centered(primary, defaultW, defaultH);
        }
        double w = Math.max(saved.width(), minWidth);
        double h = Math.max(saved.height(), minHeight);
        Rect candidate = new Rect(saved.x(), saved.y(), w, h);
        if (screens == null) {
            return candidate;
        }
        Rect screen = screens.stream().filter(candidate::intersects).findFirst().orElse(null);
        if (screen == null) {
            return centered(primary, defaultW, defaultH);
        }
        double x = Math.max(screen.x(), Math.min(candidate.x(), screen.maxX() - w));
        double y = Math.max(screen.y(), Math.min(candidate.y(), screen.maxY() - h));
        return new Rect(x, y, w, h);
    }

    private static Rect centered(Rect screen, double w, double h) {
        return new Rect(screen.x() + (screen.width() - w) / 2.0, screen.y() + (screen.height() - h) / 2.0, w, h);
    }
}
