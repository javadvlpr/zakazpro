package com.mebelsex.common;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FlywayMigrationsTest {

    @Test
    public void testMigrationRules() throws Exception {
        Path migrationDir = Paths.get("src/main/resources/db/migration");
        assertTrue(Files.exists(migrationDir), "Migration directory must exist");

        // Allowed files list (must match exactly)
        List<String> allowedFiles = List.of("V1__init.sql", "V2__order_date.sql");

        try (Stream<Path> paths = Files.list(migrationDir)) {
            List<String> actualFiles = paths
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());

            assertEquals(allowedFiles.size(), actualFiles.size(),
                    "Migration folder should contain exactly expected files");
            assertTrue(actualFiles.containsAll(allowedFiles), "Migration folder files mismatch");
        }

        // Verify that no migration contains forbidden business-data inserts
        StringBuilder allContent = new StringBuilder();
        for (String file : allowedFiles) {
            allContent.append(Files.readString(migrationDir.resolve(file)));
        }
        String content = allContent.toString().toUpperCase();

        String[] forbiddenTables = {
                "INTO CUSTOMERS",
                "INTO ORDERS",
                "INTO PAYMENTS",
                "INTO EMPLOYEES",
                "INTO WORK_LOGS",
                "INTO EXPENSES",
                "INTO SALARY_PAYMENTS",
                "INTO POSITIONS",
                "INTO OPERATION_TYPES",
                "INTO EXPENSE_ITEMS"
        };

        for (String forbiddenTable : forbiddenTables) {
            assertFalse(content.contains(forbiddenTable),
                    "Migration must not contain seed data for table: " + forbiddenTable);
        }
    }
}
