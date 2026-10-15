package com.fss.backend.shared.mongo;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fss.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs a unit of work inside a MongoDB transaction and retries it when MongoDB reports a
 * {@code TransientTransactionError} (for example a write conflict between two orders that touch the same SKU).
 * A transient error guarantees the attempt was aborted, so retrying the whole callback is safe.
 */
@Component
public class MongoTransactionRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoTransactionRunner.class);
    static final String TRANSIENT_TRANSACTION_ERROR = MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL;

    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;

    public MongoTransactionRunner(PlatformTransactionManager transactionManager,
                                  @Value("${app.mongodb.transaction-max-attempts:10}") int maxAttempts) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public <T> T inTransaction(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (RuntimeException ex) {
                if (!isTransient(ex)) {
                    throw ex;
                }
                if (attempt == maxAttempts) {
                    log.warn("MongoDB transaction still conflicting after {} attempts: {}", attempt, rootMessage(ex));
                    throw new ApiException(HttpStatus.CONFLICT, "The request conflicted with concurrent updates, please retry");
                }
                last = ex;
                log.info("Retrying MongoDB transaction after transient error attempt={} cause={}", attempt, rootMessage(ex));
                backoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("unreachable") : last;
    }

    static boolean isTransient(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MongoException mongo && mongo.hasErrorLabel(TRANSIENT_TRANSACTION_ERROR)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static void backoff(int attempt) {
        try {
            long cap = Math.min(250L, 10L * attempt * attempt);
            Thread.sleep(cap / 2 + (long) (Math.random() * (cap / 2 + 10)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying transaction", ex);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
