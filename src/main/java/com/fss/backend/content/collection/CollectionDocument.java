package com.fss.backend.content.collection;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collection {@code collections}. Product membership (old table {@code collection_products}) is embedded as
 * {@code products} because it is always replaced as a whole and bounded by what an admin curates.
 */
@Document(CollectionDocument.COLLECTION)
public record CollectionDocument(@Id UUID id, String name, String slug, String excerpt, String descriptionHtml,
                                 List<String> htmlFilePaths, UUID fileId, String status, Integer sortOrder,
                                 List<Member> products, UUID createdBy, UUID updatedBy, Instant createdAt,
                                 Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "collections";

    public record Member(UUID productId, Integer sortOrder, Instant createdAt) {}
}
