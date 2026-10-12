package com.fss.backend.common;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the most common MongoDB startup failures into an actionable message instead of a long bean-creation stack
 * trace (the failing bean is usually a repository or the index initializer, not the root cause).
 */
public class StartupFailureAnalyzer implements FailureAnalyzer {

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        List<Throwable> causes = causes(failure);
        for (Throwable cause : causes) {
            String type = cause.getClass().getName();
            String message = cause.getMessage() == null ? "" : cause.getMessage();

            if (type.equals("com.mongodb.MongoSecurityException") || message.contains("Authentication failed")) {
                return new FailureAnalysis("""
                        Cannot authenticate to MongoDB during application startup.
                        Root cause: %s
                        """.formatted(message),
                        """
                        Check the user name and password in MONGODB_URI (URL-encode special characters such as @ : / ?).
                        In MongoDB Atlas, verify the database user exists and has readWrite on the target database.
                        """, cause);
            }
            if (type.equals("com.mongodb.MongoTimeoutException") || type.equals("com.mongodb.MongoSocketOpenException")) {
                return new FailureAnalysis("""
                        Cannot reach MongoDB during application startup.
                        Root cause: %s
                        """.formatted(message),
                        """
                        Verify MONGODB_URI. For local development start MongoDB with `docker compose up -d mongo`.
                        For MongoDB Atlas, allow the Heroku dyno egress in Network Access (Heroku dynos have no fixed IP:
                        use 0.0.0.0/0 with a strong password, or a static-IP add-on / Private Space).
                        """, cause);
            }
            if (message.contains("Transaction numbers are only allowed on a replica set")
                    || message.contains("IllegalOperation") && message.contains("transaction")) {
                return new FailureAnalysis("""
                        MongoDB is running as a standalone server, but this application requires transactions.
                        Root cause: %s
                        """.formatted(message),
                        """
                        Use a replica set: MongoDB Atlas clusters are replica sets; locally use the docker-compose `mongo` service
                        (single-node replica set rs0) or start mongod with --replSet and run rs.initiate().
                        """, cause);
            }
            if (type.equals("com.mongodb.MongoConfigurationException") || message.contains("The connection string is invalid")) {
                return new FailureAnalysis("""
                        MONGODB_URI is invalid.
                        Root cause: %s
                        """.formatted(message),
                        "Use a URI such as mongodb+srv://user:password@cluster.example.mongodb.net/fss?retryWrites=true&w=majority",
                        cause);
            }
        }
        return null;
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
}
