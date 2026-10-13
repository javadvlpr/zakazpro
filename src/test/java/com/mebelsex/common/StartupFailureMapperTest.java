package com.mebelsex.common;

import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StartupFailureMapperTest {

    @Test
    void flywayNewerSchemaIsClassified() {
        Exception e = new RuntimeException(
                "Schema `main` has a version (3) that is newer than the latest available migration (2).");
        assertEquals(StartupFailureMapper.NEWER_SCHEMA_KEY, StartupFailureMapper.messageKey(e));
    }

    @Test
    void newerSchemaIsFoundInTheCauseChain() {
        Exception root = new RuntimeException("Detected applied migration not resolved locally");
        Exception wrapped = new RuntimeException("Unable to apply migration", root);
        assertEquals(StartupFailureMapper.NEWER_SCHEMA_KEY, StartupFailureMapper.messageKey(wrapped));
    }

    @Test
    void lockedDatabaseIsClassified() {
        Exception root = new RuntimeException("database is locked");
        Exception wrapped = new RuntimeException("Could not open connection", root);
        assertEquals(StartupFailureMapper.DB_LOCKED_KEY, StartupFailureMapper.messageKey(wrapped));
    }

    @Test
    void unknownFailureFallsBackToGeneric() {
        assertEquals(StartupFailureMapper.GENERIC_KEY,
                StartupFailureMapper.messageKey(new IllegalStateException("something went wrong")));
    }

    @Test
    void everyMappedKeyHasAnUzbekText() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_uz");
        assertNotNull(bundle.getString(StartupFailureMapper.NEWER_SCHEMA_KEY));
        assertNotNull(bundle.getString(StartupFailureMapper.DB_LOCKED_KEY));
        assertNotNull(bundle.getString(StartupFailureMapper.GENERIC_KEY));
        assertNotNull(bundle.getString("startup.failed.title"));
        assertNotNull(bundle.getString("startup.failed.open_logs"));
        assertNotNull(bundle.getString("startup.failed.exit"));
        assertNotNull(bundle.getString("startup.already_running.title"));
    }
}
