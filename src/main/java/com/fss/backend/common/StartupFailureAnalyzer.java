package com.fss.backend.common;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

import java.net.ConnectException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StartupFailureAnalyzer implements FailureAnalyzer {
    private static final Pattern POSTGRES_CONNECTION_REFUSED =
            Pattern.compile("Connection to ([^\\s]+) refused");
    private static final Pattern POSTGRES_JDBC_URL =
            Pattern.compile("jdbc:postgresql://([^\\s/?]+)");
    private static final Pattern POSTGRES_AUTH_FAILED =
            Pattern.compile("password authentication failed for user \"([^\"]+)\"");
    private static final Pattern POSTGRES_DATABASE_NOT_FOUND =
            Pattern.compile("database \"([^\"]+)\" does not exist");
    private static final Pattern FLYWAY_MIGRATION_FILE =
            Pattern.compile("(?:Migration|Script)\\s+([^\\s]+\\.sql)\\s+failed");
    private static final Pattern FLYWAY_VERSION =
            Pattern.compile("Migration\\s+(?:version\\s+)?([^\\s]+)");

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        Throwable startupCause = findStartupCause(failure);
        if (startupCause == null || !hasDatabaseStartupMarker(failure)) {
            return null;
        }

        FailureKind kind = resolveKind(failure);
        String target = resolveTarget(failure);
        String description = """
                %s
                Spring failed while initializing the datasource/Flyway/MyBatis stack, so beans such as AuthService or AuthMapper can appear in the stack trace but are not the root cause.

                Target database: %s
                Root cause: %s
                """.formatted(kind.description, target, cleanMessage(startupCause));

        String action = kind.action;

        return new FailureAnalysis(description, action, startupCause);
    }

    private Throwable findStartupCause(Throwable failure) {
        Throwable specific = null;
        Throwable fallback = null;
        for (Throwable candidate : causes(failure)) {
            if (candidate instanceof ConnectException
                    || candidate instanceof SQLTransientConnectionException
                    || candidate instanceof SQLNonTransientConnectionException) {
                return candidate;
            }

            String className = candidate.getClass().getName();
            String message = candidate.getMessage();
            if (className.startsWith("org.flywaydb.core.api.") && message != null) {
                specific = candidate;
                continue;
            }

            if (className.equals("org.postgresql.util.PSQLException")
                    && message != null
                    && isPostgresStartupMessage(message)) {
                specific = candidate;
                continue;
            }

            if (message != null && (isSpecificPostgresFailureMessage(message) || isSpecificFlywayFailureMessage(message))) {
                specific = candidate;
            }

            if (message != null && fallback == null && message.contains("Unable to obtain connection")) {
                fallback = candidate;
            }

            if (message != null && fallback == null && isFlywayFailureMessage(message)) {
                fallback = candidate;
            }
        }
        if (specific != null) {
            return specific;
        }
        return fallback;
    }

    private boolean isPostgresStartupMessage(String message) {
        return message.contains("Connection to")
                || message.contains("Unable to obtain connection")
                || isSpecificPostgresFailureMessage(message);
    }

    private boolean isSpecificPostgresFailureMessage(String message) {
        return message.contains("Connection to")
                || message.contains("password authentication failed for user")
                || message.contains("database \"") && message.contains("\" does not exist");
    }

    private boolean isFlywayFailureMessage(String message) {
        return isSpecificFlywayFailureMessage(message)
                || message.contains("Migration") && message.contains("failed");
    }

    private boolean isSpecificFlywayFailureMessage(String message) {
        return FLYWAY_MIGRATION_FILE.matcher(message).find()
                || message.contains("Validate failed")
                || message.contains("checksum mismatch")
                || message.contains("no schema history table")
                || message.contains("Detected failed migration")
                || message.contains("repair may be necessary");
    }

    private FailureKind resolveKind(Throwable failure) {
        String genericFlywayFailureMessage = null;
        for (Throwable candidate : causes(failure)) {
            String message = candidate.getMessage();
            if (message == null) {
                continue;
            }

            var authFailed = POSTGRES_AUTH_FAILED.matcher(message);
            if (authFailed.find()) {
                String user = authFailed.group(1);
                return new FailureKind(
                        "Cannot authenticate to PostgreSQL during application startup.",
                        """
                                Fix spring.datasource.username/password or the corresponding POSTGRES_USER/POSTGRES_PASSWORD values.
                                Current failing database user: %s.
                                If you changed docker-compose.yml credentials after the database volume was created, recreate or migrate the postgres_data volume so PostgreSQL uses the new credentials.
                                """.formatted(user)
                );
            }

            var databaseNotFound = POSTGRES_DATABASE_NOT_FOUND.matcher(message);
            if (databaseNotFound.find()) {
                String database = databaseNotFound.group(1);
                return new FailureKind(
                        "Configured PostgreSQL database does not exist during application startup.",
                        """
                                Create database `%s` or fix the database name in spring.datasource.url.
                                For the provided Docker Compose setup, POSTGRES_DB should match the database segment in spring.datasource.url.
                                If the PostgreSQL volume already exists, changing POSTGRES_DB alone will not create a new database in the existing volume.
                        """.formatted(database)
                );
            }

            if (isSpecificFlywayFailureMessage(message)) {
                return resolveFlywayKind(message);
            }

            if (genericFlywayFailureMessage == null && isFlywayFailureMessage(message)) {
                genericFlywayFailureMessage = message;
            }
        }

        if (genericFlywayFailureMessage != null) {
            return resolveFlywayKind(genericFlywayFailureMessage);
        }

        return new FailureKind(
                "Cannot connect to PostgreSQL during application startup.",
                """
                        Start the configured PostgreSQL database or fix spring.datasource.url.
                        For local development, run `docker compose up -d db` and verify that PostgreSQL is reachable on localhost:5432.
                        When running the API inside Docker Compose, use SPRING_PROFILES_ACTIVE=docker so the datasource points to jdbc:postgresql://db:5432/fss.
                        """
        );
    }

    private FailureKind resolveFlywayKind(String message) {
        String migration = resolveMigrationName(message);
        String target = migration == null ? "the failing migration" : "`" + migration + "`";

        if (message.contains("Validate failed") || message.contains("checksum mismatch")) {
            return new FailureKind(
                    "Flyway validation failed during application startup.",
                    """
                            Do not edit an already-applied migration in src/main/resources/db/migration.
                            Restore the original migration or add a new versioned migration for the schema change.
                            If this is a local-only database and the edited migration is intentional, run Flyway repair or recreate the local PostgreSQL volume after confirming no data needs to be kept.
                            """
            );
        }

        if (message.contains("no schema history table")) {
            return new FailureKind(
                    "Flyway found an existing non-empty schema without a schema history table.",
                    """
                            This database already has objects in the schema, but Flyway has not recorded any applied migrations.
                            For local development, recreate the PostgreSQL database/volume if the existing data is disposable.
                            If this database must be kept, baseline it with Flyway after confirming the current schema matches the expected starting migration.
                            Avoid setting spring.flyway.baseline-on-migrate=true blindly in shared environments because it can hide drift between the database and migration files.
                            """
            );
        }

        return new FailureKind(
                "Flyway migration failed during application startup.",
                """
                        Fix %s in src/main/resources/db/migration and restart the application.
                        Check the SQL error immediately above this failure analysis for the exact statement and PostgreSQL error.
                        If the migration partially ran locally, repair or recreate the local database before retrying.
                        """.formatted(target)
        );
    }

    private String resolveMigrationName(String message) {
        var file = FLYWAY_MIGRATION_FILE.matcher(message);
        if (file.find()) {
            return file.group(1);
        }

        var version = FLYWAY_VERSION.matcher(message);
        if (version.find()) {
            return version.group(1);
        }

        return null;
    }

    private boolean hasDatabaseStartupMarker(Throwable failure) {
        for (Throwable candidate : causes(failure)) {
            String value = candidate.getClass().getName() + " " + candidate.getMessage();
            if (value.contains("flywayInitializer")
                    || value.contains("Flyway")
                    || value.contains("sqlSessionTemplate")
                    || value.contains("DataSource")
                    || value.contains("datasource")
                    || value.contains("Hikari")
                    || value.contains("jdbc:postgresql")) {
                return true;
            }
        }
        return false;
    }

    private String resolveTarget(Throwable failure) {
        for (Throwable candidate : causes(failure)) {
            String message = candidate.getMessage();
            if (message == null) {
                continue;
            }

            var refused = POSTGRES_CONNECTION_REFUSED.matcher(message);
            if (refused.find()) {
                return refused.group(1);
            }

            var jdbcUrl = POSTGRES_JDBC_URL.matcher(message);
            if (jdbcUrl.find()) {
                return jdbcUrl.group(1);
            }
        }
        return "configured PostgreSQL datasource";
    }

    private List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        Throwable current = failure;
        while (current != null && !causes.contains(current)) {
            causes.add(current);
            current = current.getCause();
        }
        return causes;
    }

    private String cleanMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record FailureKind(String description, String action) {
    }
}
