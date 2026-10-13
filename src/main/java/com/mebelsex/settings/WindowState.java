package com.mebelsex.settings;

/**
 * Saved main-window geometry. Any of x/y/width/height may be null when never saved.
 */
public record WindowState(Integer x, Integer y, Integer width, Integer height, boolean maximized) {

    public boolean hasBounds() {
        return x != null && y != null && width != null && height != null && width > 0 && height > 0;
    }
}
