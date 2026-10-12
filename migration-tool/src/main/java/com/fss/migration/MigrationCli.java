package com.fss.migration;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.UuidRepresentation;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * One-off migration CLI. Never run automatically on application boot.
 *
 * <pre>
 *   migrate-db   copy PostgreSQL -> MongoDB (idempotent upserts, PostgreSQL read-only)   [--dry-run]
 *   verify       compare counts/totals between PostgreSQL and MongoDB (read-only)
 *   sync-files   copy uploads/ -> R2 files/ and switch metadata to r2 (idempotent)      [--dry-run]
 * </pre>
 * Connection settings come from options or environment variables: PG_URL, PG_USER, PG_PASSWORD, MONGODB_URI,
 * MONGODB_DATABASE, UPLOAD_DIR, R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY.
 */
public final class MigrationCli {
    private MigrationCli() {
    }

    public static void main(String[] argv) throws Exception {
        System.exit(run(argv));
    }

    public static int run(String[] argv) throws Exception {
        if (argv.length == 0 || !List.of("migrate-db", "verify", "sync-files").contains(argv[0])) {
            System.err.println("Usage: migration-tool <migrate-db|verify|sync-files> [options]\n"
                    + "  --pg-url jdbc:postgresql://host:5432/fss --pg-user u --pg-password p\n"
                    + "  --mongo-uri mongodb+srv://... --mongo-db fss\n"
                    + "  --upload-dir ./uploads --r2-endpoint https://<account>.r2.cloudflarestorage.com --r2-bucket b\n"
                    + "  --r2-access-key-id k --r2-secret-access-key s\n"
                    + "  --dry-run  --allow-extra-documents");
            return 2;
        }
        Args args = new Args(argv, 1);
        boolean dryRun = args.flag("dry-run");
        try (MongoClient client = mongoClient(args.require("mongo-uri", "MONGODB_URI"))) {
            MongoDatabase mongo = client.getDatabase(args.get("mongo-db", "MONGODB_DATABASE") == null ? "fss"
                    : args.get("mongo-db", "MONGODB_DATABASE"));
            switch (argv[0]) {
                case "migrate-db" -> {
                    try (Connection pg = postgres(args)) {
                        new PgToMongoMigrator(pg, mongo, dryRun, args.flag("allow-extra-documents")).migrateAll();
                    }
                    System.out.println(dryRun ? "Dry run finished: nothing was written." : "Migration finished. Run `verify` next.");
                    return 0;
                }
                case "verify" -> {
                    try (Connection pg = postgres(args)) {
                        List<String> problems = new MigrationVerifier(pg, mongo).verify();
                        System.out.println(problems.isEmpty() ? "Verification OK" : "Verification FAILED: " + problems);
                        return problems.isEmpty() ? 0 : 1;
                    }
                }
                default -> {
                    try (S3Client s3 = r2(args)) {
                        int missing = new FileSync(mongo, s3, args.require("r2-bucket", "R2_BUCKET"),
                                Path.of(args.require("upload-dir", "UPLOAD_DIR")), dryRun).run();
                        return missing == 0 ? 0 : 1;
                    }
                }
            }
        }
    }

    static MongoClient mongoClient(String uri) {
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    }

    private static Connection postgres(Args args) throws Exception {
        Connection connection = DriverManager.getConnection(args.require("pg-url", "PG_URL"),
                args.require("pg-user", "PG_USER"), args.require("pg-password", "PG_PASSWORD"));
        connection.setReadOnly(true);
        return connection;
    }

    private static S3Client r2(Args args) {
        return S3Client.builder()
                .endpointOverride(URI.create(args.require("r2-endpoint", "R2_ENDPOINT")))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        args.require("r2-access-key-id", "R2_ACCESS_KEY_ID"),
                        args.require("r2-secret-access-key", "R2_SECRET_ACCESS_KEY"))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
