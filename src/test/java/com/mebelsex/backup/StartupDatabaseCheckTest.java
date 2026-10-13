package com.mebelsex.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupDatabaseCheckTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileIsFirstRun() {
        assertEquals(StartupDatabaseCheck.Status.FIRST_RUN, StartupDatabaseCheck.check(tempDir.resolve("mebel.db")));
    }

    @Test
    void zeroByteFileIsFirstRun() throws Exception {
        Path db = tempDir.resolve("empty.db");
        Files.createFile(db);
        assertEquals(StartupDatabaseCheck.Status.FIRST_RUN, StartupDatabaseCheck.check(db));
    }

    @Test
    void validDatabaseIsOk() throws Exception {
        Path db = tempDir.resolve("valid.db");
        createDatabase(db);
        assertEquals(StartupDatabaseCheck.Status.OK, StartupDatabaseCheck.check(db));
    }

    @Test
    void textFileRenamedToDbIsDamaged() throws Exception {
        Path db = tempDir.resolve("fake.db");
        Files.writeString(db, "this is definitely not a sqlite database, just some text");
        assertEquals(StartupDatabaseCheck.Status.DAMAGED, StartupDatabaseCheck.check(db));
    }

    @Test
    void truncatedDatabaseIsDamaged() throws Exception {
        Path valid = tempDir.resolve("valid.db");
        createDatabase(valid);
        byte[] bytes = Files.readAllBytes(valid);
        Path truncated = tempDir.resolve("truncated.db");
        Files.write(truncated, java.util.Arrays.copyOf(bytes, Math.min(100, bytes.length)));
        assertEquals(StartupDatabaseCheck.Status.DAMAGED, StartupDatabaseCheck.check(truncated));
    }

    private static void createDatabase(Path file) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (id INTEGER)");
            statement.execute("INSERT INTO t (id) VALUES (1)");
        }
    }
}
