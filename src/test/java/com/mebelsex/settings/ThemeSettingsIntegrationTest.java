package com.mebelsex.settings;

import com.mebelsex.ui.Theme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-theme-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class ThemeSettingsIntegrationTest {

    @Autowired
    private SettingsService settingsService;

    @Test
    void missingKeyDefaultsToLight() {
        assertEquals(SettingsService.DEFAULT_THEME, settingsService.loadTheme());
        assertEquals(Theme.LIGHT, Theme.parse(settingsService.loadTheme()));
    }

    @Test
    void themeRoundTrips() {
        settingsService.saveTheme("DARK");
        assertEquals("DARK", settingsService.loadTheme());
        assertEquals(Theme.DARK, Theme.parse(settingsService.loadTheme()));
    }

    @Test
    void invalidStoredValueFallsBackToLight() {
        settingsService.saveTheme("BANANA");
        assertEquals(Theme.LIGHT, Theme.parse(settingsService.loadTheme()));
    }
}
