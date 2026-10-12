package com.fss.backend.file;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Collection {@code files}: metadata only, never image bytes.
 *
 * <ul>
 *   <li>{@code path}: provider-independent relative path ({@code yyyy-MM-dd/<uuid>-name.jpg}). Public URLs and
 *       HTML references are {@code <app.cdn.base-url>/<path>}, so the hostname is never stored.</li>
 *   <li>{@code objectKey}: key inside the storage provider (R2: {@code files/<path>}; local: {@code <path>}).</li>
 *   <li>{@code statusChangedAt}: when the file last changed status; the cleanup TTL is measured from it.</li>
 *   <li>{@code leaseUntil}/{@code deleteAttempts}/{@code lastError}: cleanup bookkeeping for retries across instances.</li>
 * </ul>
 */
@Document(FileDocument.COLLECTION)
public record FileDocument(@Id UUID id, String path, String objectKey, String storageProvider, String status,
                           String contentType, Long sizeBytes, Integer width, Integer height,
                           UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, Instant statusChangedAt,
                           Instant leaseUntil, Integer deleteAttempts, String lastError) {
    public static final String COLLECTION = "files";

    public FileAsset toAsset() {
        return new FileAsset(id, path, status, com.fss.backend.shared.mongo.MongoValues.odt(createdAt));
    }
}
