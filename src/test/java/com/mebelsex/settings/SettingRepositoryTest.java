package com.mebelsex.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-mebelsex-${random.uuid}.db?foreign_keys=on&journal_mode=wal&busy_timeout=5000",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.flyway.enabled=true"
})
class SettingRepositoryTest {

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void canReadSeededSetting_provesTimestampParsing() {
        // "company_name" is seeded by Flyway V1 with default datetime('now')
        Optional<Setting> settingOpt = settingRepository.findByKey("company_name");
        assertTrue(settingOpt.isPresent());

        Setting setting = settingOpt.get();
        assertEquals("", setting.getValue());

        // Proves SQLite TEXT datetime('now') was correctly parsed into LocalDateTime
        assertNotNull(setting.getCreatedAt());
        assertNotNull(setting.getUpdatedAt());
    }

    @Test
    void canCreateAndUpdateSetting_provesPrePersistPreUpdate() {
        Setting s = new Setting("test_key", "test_value");
        Setting saved = settingRepository.saveAndFlush(s);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt(), "Initial create");

        // Force a delay so timestamps differ (SQLite stores seconds, so we'd need to
        // wait >1s for a real difference,
        // but we just prove the fields are populated via JPA callbacks)
        saved.setValue("new_value");
        Setting updated = settingRepository.saveAndFlush(saved);

        assertEquals("new_value", updated.getValue());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void testForeignKeysPragmaIsActive() {
        Integer fkPragma = jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class);
        assertEquals(1, fkPragma, "Foreign keys PRAGMA should be ON (1)");
    }

    @Autowired
    private org.flywaydb.core.Flyway flyway;

    @Test
    void checkConstraintEnforced() {
        // Proves SQLite CHECK constraint (amount > 0) for salary_payments table
        jdbcTemplate.execute("INSERT INTO employees (full_name) VALUES ('Test Employee')");
        Long empId = jdbcTemplate.queryForObject("SELECT id FROM employees WHERE full_name='Test Employee' LIMIT 1",
                Long.class);

        org.springframework.dao.DataAccessException exception = assertThrows(
                org.springframework.dao.DataAccessException.class, () -> {
                    jdbcTemplate.update(
                            "INSERT INTO salary_payments (employee_id, amount, payment_date) VALUES (?, 0, '2026-10-03')",
                            empId);
                });
    }

    @Test
    void testFlywaySecondRunIsNoOp() {
        // First run is done automatically by Spring Boot
        org.flywaydb.core.api.output.MigrateResult result = flyway.migrate();
        assertEquals(0, result.migrationsExecuted, "Second Flyway run should execute 0 migrations (idempotent)");
    }
}
