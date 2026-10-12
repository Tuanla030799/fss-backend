package com.fss.backend.catalog.product;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(VariantDocument.COLLECTION)
public record VariantDocument(@Id UUID id, UUID productId, String name, UUID colorId, UUID imageFileId, String status,
                              Integer sortOrder, Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "product_variants";
}
