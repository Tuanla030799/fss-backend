package com.fss.backend.common;

import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFailureAnalyzerTest {

    @Test
    void explainsUnreachableMongo() {
        var cause = new MongoTimeoutException("Timed out after 30000 ms while waiting to connect");
        var analysis = new StartupFailureAnalyzer().analyze(new BeanCreationException("mongoIndexInitializer", "failed", cause));

        assertNotNull(analysis);
        assertEquals(cause, analysis.getCause());
        assertTrue(analysis.getDescription().contains("Cannot reach MongoDB"));
        assertTrue(analysis.getAction().contains("MONGODB_URI"));
    }

    @Test
    void explainsAuthenticationFailure() {
        var cause = new MongoSecurityException(null, "Exception authenticating MongoCredential");
        var analysis = new StartupFailureAnalyzer().analyze(new BeanCreationException("x", "failed", cause));

        assertNotNull(analysis);
        assertTrue(analysis.getDescription().contains("Cannot authenticate to MongoDB"));
    }

    @Test
    void explainsStandaloneServerWithoutTransactions() {
        var cause = new IllegalStateException("MongoDB is a standalone server: Transaction numbers are only allowed on a replica set member or mongos.");
        var analysis = new StartupFailureAnalyzer().analyze(new RuntimeException("startup", cause));

        assertNotNull(analysis);
        assertTrue(analysis.getAction().contains("replica set"));
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertNull(new StartupFailureAnalyzer().analyze(new IllegalStateException("Something else")));
    }
}
