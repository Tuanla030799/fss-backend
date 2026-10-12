package com.fss.backend.catalog.brand;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code brands}. {@code fileId} is the logo, {@code fileSizeId} the size-guide image. */
@Document(BrandDocument.COLLECTION)
public record BrandDocument(@Id UUID id, String name, String slug, String description, UUID fileId, UUID fileSizeId,
                            String status, Integer sortOrder, UUID createdBy, UUID updatedBy, Instant createdAt,
                            Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "brands";
}
