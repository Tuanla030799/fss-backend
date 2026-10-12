package com.fss.backend.catalog.category;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(CategoryDocument.COLLECTION)
public record CategoryDocument(@Id UUID id, UUID parentId, String name, String slug, String description, String status,
                               Integer sortOrder, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                               boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "categories";

    public Category toCategory() {
        return new Category(id, parentId, name, slug, description, status, sortOrder,
                com.fss.backend.shared.mongo.MongoValues.odt(createdAt));
    }
}
