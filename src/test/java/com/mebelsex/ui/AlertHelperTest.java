package com.mebelsex.ui;

import javafx.scene.control.ButtonBar;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertHelperTest {

    @Test
    void testConfirmButtonsLetWindowClose() {
        // CANCEL_CLOSE is what lets the window X and Esc close the dialog and return "no".
        assertEquals(ButtonBar.ButtonData.YES, AlertHelper.YES_BUTTON.getButtonData());
        assertEquals(ButtonBar.ButtonData.CANCEL_CLOSE, AlertHelper.NO_BUTTON.getButtonData());
    }

    @Test
    void testGetActiveWindowSelectsShowingWindow() {
        Window w1 = mock(Window.class);
        when(w1.isShowing()).thenReturn(false);

        Window w2 = mock(Window.class);
        when(w2.isShowing()).thenReturn(true);

        Window w3 = mock(Window.class);
        when(w3.isShowing()).thenReturn(true);

        Window result = AlertHelper.getActiveWindow(List.of(w1, w2, w3));
        assertEquals(w2, result, "Should pick the first showing window");
    }

    @Test
    void testGetActiveWindowReturnsNullIfNoneShowing() {
        Window w1 = mock(Window.class);
        when(w1.isShowing()).thenReturn(false);

        Window w2 = mock(Window.class);
        when(w2.isShowing()).thenReturn(false);

        Window result = AlertHelper.getActiveWindow(List.of(w1, w2));
        assertNull(result, "Should return null if no window is showing");
    }
}
