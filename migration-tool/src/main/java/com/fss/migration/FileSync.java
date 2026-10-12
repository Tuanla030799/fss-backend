package com.fss.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Copies files of the old local storage ({@code uploads/<path>}) to R2 under {@code files/<path>} and switches their
 * metadata to {@code storageProvider=r2}. Idempotent: objects already present with the same size are skipped, and
 * metadata is only updated after the object exists. Local files are never deleted.
 */
final class FileSync {
    private final MongoDatabase mongo;
    private final S3Client s3;
    private final String bucket;
    private final Path uploadDir;
    private final boolean dryRun;

    FileSync(MongoDatabase mongo, S3Client s3, String bucket, Path uploadDir, boolean dryRun) {
        this.mongo = mongo;
        this.s3 = s3;
        this.bucket = bucket;
        this.uploadDir = uploadDir.toAbsolutePath().normalize();
        this.dryRun = dryRun;
    }

    int run() throws IOException {
        MongoCollection<Document> files = mongo.getCollection("files");
        int uploaded = 0, skipped = 0, missing = 0, switched = 0;
        for (Document file : files.find(new Document("storageProvider", new Document("$ne", "r2")))) {
            String path = file.getString("path");
            Path local = uploadDir.resolve(path).normalize();
            if (path == null || !local.startsWith(uploadDir) || !Files.isRegularFile(local)) {
                missing++;
                System.out.println("  missing local file for fileId=" + file.get("_id") + " path=" + path);
                continue;
            }
            String key = "files/" + path;
            long size = Files.size(local);
            if (existsWithSize(key, size)) {
                skipped++;
            } else if (!dryRun) {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType(PgToMongoMigrator.contentType(path))
                        .cacheControl("public, max-age=31536000, immutable")
                        .contentLength(size).build(), RequestBody.fromFile(local));
                uploaded++;
            }
            if (!dryRun) {
                files.updateOne(new Document("_id", file.get("_id")), new Document("$set", new Document("storageProvider", "r2")
                        .append("objectKey", key).append("sizeBytes", size).append("updatedAt", Instant.now())));
                switched++;
            }
        }
        System.out.printf("uploaded=%d alreadyPresent=%d metadataSwitched=%d missingLocal=%d%s%n", uploaded, skipped, switched,
                missing, dryRun ? " (dry run)" : "");
        return missing;
    }

    private boolean existsWithSize(String key, long size) {
        try {
            return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength() == size;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return false;
            throw ex;
        }
    }
}
