package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowGeometryTest {

    private static final List<WindowGeometry.Rect> SCREENS =
            List.of(new WindowGeometry.Rect(0, 0, 1920, 1080));

    @Test
    void offScreenFallsBackToCenteredDefault() {
        WindowGeometry.Rect r = WindowGeometry.resolve(
                new WindowGeometry.Rect(5000, 5000, 1000, 700), SCREENS, 1024, 680);
        assertEquals(WindowGeometry.DEFAULT_WIDTH, r.width());
        assertEquals(WindowGeometry.DEFAULT_HEIGHT, r.height());
        assertEquals((1920 - WindowGeometry.DEFAULT_WIDTH) / 2.0, r.x());
        assertEquals((1080 - WindowGeometry.DEFAULT_HEIGHT) / 2.0, r.y());
    }

    @Test
    void partlyOffScreenIsClampedInsideTheScreen() {
        WindowGeometry.Rect r = WindowGeometry.resolve(
                new WindowGeometry.Rect(-100, -50, 1100, 700), SCREENS, 1024, 680);
        assertTrue(r.x() >= 0, "x must be inside the screen");
        assertTrue(r.y() >= 0, "y must be inside the screen");
        assertTrue(r.maxX() <= 1920, "right edge must be inside the screen");
        assertTrue(r.maxY() <= 1080, "bottom edge must be inside the screen");
    }

    @Test
    void smallerThanTheMinimumIsRaisedToTheMinimum() {
        WindowGeometry.Rect r = WindowGeometry.resolve(
                new WindowGeometry.Rect(10, 10, 300, 200), SCREENS, 1024, 680);
        assertEquals(1024, r.width());
        assertEquals(680, r.height());
    }

    @Test
    void maximizedSizedRectStaysWithinTheScreen() {
        WindowGeometry.Rect r = WindowGeometry.resolve(
                new WindowGeometry.Rect(0, 0, 1920, 1080), SCREENS, 1024, 680);
        assertEquals(0, r.x());
        assertEquals(0, r.y());
        assertEquals(1920, r.width());
        assertEquals(1080, r.height());
    }

    @Test
    void noSavedStateCentersTheDefault() {
        WindowGeometry.Rect r = WindowGeometry.resolve(null, SCREENS, 1024, 680);
        assertEquals(WindowGeometry.DEFAULT_WIDTH, r.width());
        assertEquals((1920 - WindowGeometry.DEFAULT_WIDTH) / 2.0, r.x());
    }

    @Test
    void matchesTheSecondScreenWhenItIntersectsIt() {
        List<WindowGeometry.Rect> screens = List.of(
                new WindowGeometry.Rect(0, 0, 1920, 1080),
                new WindowGeometry.Rect(1920, 0, 1920, 1080));
        WindowGeometry.Rect r = WindowGeometry.resolve(
                new WindowGeometry.Rect(2000, 100, 1000, 700), screens, 1024, 680);
        assertTrue(r.x() >= 1920, "must stay on the second screen");
        assertTrue(r.maxX() <= 3840);
    }
}
