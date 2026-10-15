package com.fss.backend.it;

import com.fss.migration.MigrationCli;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.sun.net.httpserver.HttpServer;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** migration-tool sync-files against a fake S3 endpoint: copies uploads/ to files/<path>, idempotent, keeps local files. */
class FileSyncIntegrationTest {

    @Test
    void syncCopiesLocalUploadsOnceAndSwitchesMetadata() throws Exception {
        Map<String, byte[]> bucket = new ConcurrentHashMap<>();
        AtomicInteger puts = new AtomicInteger();
        HttpServer s3 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s3.createContext("/", exchange -> {
            String key = exchange.getRequestURI().getPath().substring("/shoes/".length());
            byte[] body = exchange.getRequestBody().readAllBytes();
            switch (exchange.getRequestMethod()) {
                case "PUT" -> {
                    bucket.put(key, body);
                    puts.incrementAndGet();
                    exchange.sendResponseHeaders(200, -1);
                }
                case "HEAD" -> {
                    byte[] stored = bucket.get(key);
                    if (stored == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.getResponseHeaders().add("Content-Length", String.valueOf(stored.length));
                        exchange.sendResponseHeaders(200, -1);
                    }
                }
                default -> exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
        s3.start();

        String database = "fss_sync";
        Path uploads = Files.createTempDirectory("uploads");
        Files.createDirectories(uploads.resolve("2026-06-01"));
        Files.write(uploads.resolve("2026-06-01/a b.jpg"), new byte[]{1, 2, 3});
        UUID present = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        try (MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MongoIntegrationTest.MONGO.getReplicaSetUrl(database)))
                .uuidRepresentation(UuidRepresentation.STANDARD).build())) {
            var files = client.getDatabase(database).getCollection("files");
            files.insertOne(new Document("_id", present).append("path", "2026-06-01/a b.jpg").append("storageProvider", "local"));
            files.insertOne(new Document("_id", missing).append("path", "2026-06-01/gone.jpg").append("storageProvider", "local"));

            String[] args = {"sync-files", "--mongo-uri", MongoIntegrationTest.MONGO.getReplicaSetUrl(database), "--mongo-db", database,
                    "--upload-dir", uploads.toString(), "--r2-endpoint", "http://127.0.0.1:" + s3.getAddress().getPort(),
                    "--r2-bucket", "shoes", "--r2-access-key-id", "k", "--r2-secret-access-key", "s"};
            assertEquals(1, MigrationCli.run(args), "missing local files are reported with exit code 1");
            assertEquals(1, MigrationCli.run(args));

            assertEquals(1, puts.get(), "second run must not upload again");
            assertArrayEquals(new byte[]{1, 2, 3}, bucket.get("files/2026-06-01/a b.jpg"));
            Document migrated = files.find(new Document("_id", present)).first();
            assertEquals("r2", migrated.getString("storageProvider"));
            assertEquals("files/2026-06-01/a b.jpg", migrated.getString("objectKey"));
            assertEquals("local", files.find(new Document("_id", missing)).first().getString("storageProvider"));
            assertEquals(3, Files.size(uploads.resolve("2026-06-01/a b.jpg")), "local files are never deleted");
        } finally {
            s3.stop(0);
        }
    }
}
