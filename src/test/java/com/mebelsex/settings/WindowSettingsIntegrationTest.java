package com.mebelsex.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-window-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class WindowSettingsIntegrationTest {

    @Autowired
    private SettingsService settingsService;

    @Test
    void windowGeometryRoundTrips() {
        settingsService.saveWindow(new WindowState(120, 80, 1280, 800, false));
        WindowState state = settingsService.loadWindow();
        assertTrue(state.hasBounds());
        assertEquals(120, state.x());
        assertEquals(80, state.y());
        assertEquals(1280, state.width());
        assertEquals(800, state.height());
        assertFalse(state.maximized());
    }

    @Test
    void maximizedFlagRoundTrips() {
        settingsService.saveWindow(new WindowState(0, 0, 1600, 900, true));
        assertTrue(settingsService.loadWindow().maximized());
    }

    @Test
    void missingStateIsEmpty() {
        WindowState state = settingsService.loadWindow();
        assertFalse(state.hasBounds());
        assertNull(state.x());
        assertNull(state.width());
        assertFalse(state.maximized());
    }
}
