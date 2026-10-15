package com.fss.backend.masterdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code colors}. {@code nameKey} is the lower-cased name used for case-insensitive uniqueness. */
@Document(ColorDocument.COLLECTION)
public record ColorDocument(@Id UUID id, String name, String nameKey, String colorCode, String status, Integer sortOrder,
                            UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                            boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "colors";

    public ColorOption toOption() {
        return new ColorOption(id, name, name, colorCode, status, sortOrder, com.fss.backend.shared.mongo.MongoValues.odt(createdAt));
    }
}
