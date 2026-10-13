package com.mebelsex.settings;

import com.mebelsex.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-settings-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class SettingsServiceIntegrationTest {

    @Autowired
    private SettingsService settingsService;

    @Test
    void defaultsAreReturnedWhenKeysAreMissingOrEmpty() {
        AppSettings settings = settingsService.load();
        assertEquals("", settings.companyName());
        assertEquals("", settings.companyPhone());
        assertEquals("", settings.companyAddress());
        assertTrue(settings.backupAutoEnabled(), "auto backup defaults to on");
        assertEquals("00:00", settings.backupTime());
        assertTrue(settings.backupOnClose(), "close backup defaults to on");
        assertEquals(60, settings.backupKeepDays());
        assertNull(settings.backupLastSuccess());
    }

    @Test
    void companyAndBackupRoundTrip() {
        settingsService.saveCompany("Mebel Sex MChJ", "+998901112233", "Toshkent, Chilonzor");
        settingsService.saveBackup("D:/Backup", false, "13:05", false, 30);

        AppSettings settings = settingsService.load();
        assertEquals("Mebel Sex MChJ", settings.companyName());
        assertEquals("+998901112233", settings.companyPhone());
        assertEquals("Toshkent, Chilonzor", settings.companyAddress());
        assertEquals("D:/Backup", settings.backupFolder());
        assertFalse(settings.backupAutoEnabled());
        assertEquals("13:05", settings.backupTime());
        assertFalse(settings.backupOnClose());
        assertEquals(30, settings.backupKeepDays());
    }

    @Test
    void invalidTimeIsRejected() {
        assertThrows(BusinessException.class,
                () -> settingsService.saveBackup("D:/Backup", true, "25:00", true, 60));
        assertThrows(BusinessException.class,
                () -> settingsService.saveBackup("D:/Backup", true, "7:5", true, 60));
    }

    @Test
    void keepDaysRangeIsEnforced() {
        assertThrows(BusinessException.class,
                () -> settingsService.saveBackup("D:/Backup", true, "00:00", true, -1));
        assertThrows(BusinessException.class,
                () -> settingsService.saveBackup("D:/Backup", true, "00:00", true, 3651));
        settingsService.saveBackup("D:/Backup", true, "00:00", true, 0);
        assertEquals(0, settingsService.load().backupKeepDays());
    }

    @Test
    void lastSuccessAndAttemptErrorRoundTrip() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 4, 8, 30, 15);
        settingsService.saveLastSuccess(now);
        settingsService.saveAttemptError("disk full");

        AppSettings settings = settingsService.load();
        assertEquals(now, settings.backupLastSuccess());
        assertEquals("disk full", settings.backupLastAttemptError());

        settingsService.saveAttemptError(null);
        assertEquals("", settingsService.load().backupLastAttemptError());
    }

    @Test
    void backupFolderFallsBackToDefaultWhenEmpty() {
        assertEquals(settingsService.defaultBackupFolder(), settingsService.backupFolder());
    }
}
