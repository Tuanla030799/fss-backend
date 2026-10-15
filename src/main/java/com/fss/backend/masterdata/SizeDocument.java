package com.fss.backend.masterdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code sizes}. {@code valueKey} is the lower-cased value used for case-insensitive uniqueness. */
@Document(SizeDocument.COLLECTION)
public record SizeDocument(@Id UUID id, String value, String valueKey, String label, String status, Integer sortOrder,
                           UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                           boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "sizes";

    public SizeOption toOption() {
        return new SizeOption(id, value, label, status, sortOrder, com.fss.backend.shared.mongo.MongoValues.odt(createdAt));
    }
}
