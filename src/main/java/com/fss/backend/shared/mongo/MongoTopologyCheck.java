package com.fss.backend.shared.mongo;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Orders, stock and coupons rely on multi-document transactions, which MongoDB only supports on replica sets and
 * sharded clusters. Fail at boot instead of on the first order when connected to a standalone server.
 */
@Component
@Order(-1)
public class MongoTopologyCheck implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoTopologyCheck.class);

    private final MongoTemplate mongo;
    private final boolean requireTransactions;

    public MongoTopologyCheck(MongoTemplate mongo,
                              @Value("${app.mongodb.require-transactions:true}") boolean requireTransactions) {
        this.mongo = mongo;
        this.requireTransactions = requireTransactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        Document hello = mongo.executeCommand(new Document("hello", 1));
        boolean replicaSet = hello.containsKey("setName");
        boolean mongos = "isdbgrid".equals(hello.getString("msg"));
        log.info("MongoDB connected replicaSet={} mongos={} maxWireVersion={}", hello.get("setName"), mongos,
                hello.get("maxWireVersion"));
        if (!replicaSet && !mongos) {
            String message = "MongoDB is a standalone server: transactions (orders/stock/coupons) are not available. "
                    + "Transaction numbers are only allowed on a replica set member or mongos. "
                    + "Use MongoDB Atlas or the docker-compose single-node replica set.";
            if (requireTransactions) {
                throw new IllegalStateException(message);
            }
            log.error(message);
        }
    }
}
