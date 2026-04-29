package com.fss.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFailureAnalyzerTest {

    @Test
    void analyzeShouldExplainDatabaseStartupFailure() {
        var cause = new ConnectException("Connection to localhost:5432 refused");
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Unable to obtain connection from database",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Target database: localhost:5432"));
        assertTrue(analysis.getAction().contains("docker compose up -d db"));
    }

    @Test
    void analyzeShouldExplainAuthenticationFailure() {
        var cause = new RuntimeException("FATAL: password authentication failed for user \"postgres\"");
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Unable to obtain connection from database",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Cannot authenticate to PostgreSQL"));
        assertTrue(analysis.getAction().contains("spring.datasource.username/password"));
        assertTrue(analysis.getAction().contains("postgres"));
    }

    @Test
    void analyzeShouldExplainMissingDatabaseFailure() {
        var cause = new RuntimeException("FATAL: database \"fss\" does not exist");
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Unable to obtain connection from database",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("database does not exist"));
        assertTrue(analysis.getAction().contains("Create database `fss`"));
    }

    @Test
    void analyzeShouldExplainFlywayMigrationFailure() {
        var cause = new RuntimeException("""
                Migration V3__broken.sql failed
                --------------------------------
                SQL State  : 42601
                Message    : ERROR: syntax error at or near "BROKEN"
                """);
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Migration failed",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Flyway migration failed"));
        assertTrue(analysis.getAction().contains("`V3__broken.sql`"));
        assertTrue(analysis.getAction().contains("src/main/resources/db/migration"));
    }

    @Test
    void analyzeShouldExplainFlywayValidationFailure() {
        var cause = new RuntimeException("Validate failed: Migration checksum mismatch for migration version 1");
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Flyway validation failed",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Flyway validation failed"));
        assertTrue(analysis.getAction().contains("Do not edit an already-applied migration"));
        assertTrue(analysis.getAction().contains("Flyway repair"));
    }

    @Test
    void analyzeShouldExplainFlywayBaselineFailure() {
        var cause = new RuntimeException(
                "Found non-empty schema(s) \"public\" but no schema history table. " +
                        "Use baseline() or set baselineOnMigrate to true to initialize the schema history table."
        );
        var failure = new BeanCreationException(
                "flywayInitializer",
                "Migration failed",
                cause
        );

        var analysis = new StartupFailureAnalyzer().analyze(failure);

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("existing non-empty schema"));
        assertTrue(analysis.getAction().contains("recreate the PostgreSQL database/volume"));
        assertTrue(analysis.getAction().contains("Avoid setting spring.flyway.baseline-on-migrate=true blindly"));
    }

    @Test
    void analyzeShouldPreferDeepestSpecificStartupCause() {
        var root = new RuntimeException(
                "Found non-empty schema(s) \"public\" but no schema history table. " +
                        "Use baseline() or set baselineOnMigrate to true to initialize the schema history table."
        );
        var wrapper = new BeanCreationException(
                "authController",
                "Error creating bean with name 'flywayInitializer': " + root.getMessage(),
                root
        );

        var analysis = new StartupFailureAnalyzer().analyze(wrapper);

        assertNotNull(analysis);
        assertEquals(root, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Root cause: Found non-empty schema"));
    }

    @Test
    void analyzeShouldIgnoreUnrelatedFailures() {
        var analysis = new StartupFailureAnalyzer()
                .analyze(new IllegalStateException("regular application failure"));

        assertNull(analysis);
    }
}
