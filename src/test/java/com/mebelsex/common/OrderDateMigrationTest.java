package com.mebelsex.common;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V2 adds orders.order_date and backfills it from created_at (both the SQLite
 * "yyyy-MM-dd HH:mm:ss" form and the ISO form). This test migrates a temp DB to
 * V1 only, inserts two order rows, migrates to latest and checks the backfill.
 */
public class OrderDateMigrationTest {

    @Test
    public void backfillsOrderDateFromCreatedAt() throws Exception {
        Path dbFile = Paths.get("target", "orderdate-migration-" + UUID.randomUUID() + ".db");
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

        // 1. Migrate to V1 only.
        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        // 2. Insert rows directly: one SQLite datetime, one ISO-8601.
        try (Connection conn = DriverManager.getConnection(url); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO customers (full_name) VALUES ('Migratsiya Mijozi')");
            int customerId;
            try (ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                customerId = rs.getInt(1);
            }
            st.executeUpdate("INSERT INTO orders (order_number, customer_id, product_description, agreed_price, "
                    + "created_at, updated_at) VALUES ('1', " + customerId + ", 'Stol', 100, "
                    + "'2026-03-15 10:20:30', '2026-03-15 10:20:30')");
            st.executeUpdate("INSERT INTO orders (order_number, customer_id, product_description, agreed_price, "
                    + "created_at, updated_at) VALUES ('2', " + customerId + ", 'Divan', 200, "
                    + "'2026-04-02T08:00:00', '2026-04-02T08:00:00')");
        }

        // 3. Migrate to latest (V2 runs).
        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // 4. Assert the backfill.
        List<String> orderDates = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT order_date FROM orders ORDER BY id")) {
            while (rs.next()) {
                orderDates.add(rs.getString(1));
            }
        }
        assertEquals(List.of("2026-03-15", "2026-04-02"), orderDates);
    }
}
