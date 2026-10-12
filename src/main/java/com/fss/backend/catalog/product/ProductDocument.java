package com.fss.backend.catalog.product;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collection {@code products}. Gallery images are embedded (they are always replaced together with the product);
 * variants and SKUs live in their own collections because they are edited individually, referenced by orders and
 * SKU stock must be updated atomically on its own.
 *
 * <p>{@code htmlFilePaths} lists the uploaded files referenced from {@code descriptionHtml} so the file cleanup can
 * check usage with an index instead of scanning HTML. The legacy Editor.js {@code description_json} column was already
 * cleared to {@code {}} (V13) and is not stored anymore; the API keeps returning {@code "{}"}.
 */
@Document(ProductDocument.COLLECTION)
public record ProductDocument(@Id UUID id, UUID categoryId, UUID brandId, String gender, String name, String slug,
                              String shortDescription, String descriptionHtml, List<String> htmlFilePaths,
                              String status, Boolean isFeatured, Integer featuredOrder, List<Image> images,
                              UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                              boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "products";

    public record Image(UUID id, UUID fileId, String altText, String imageType, Integer sortOrder, Boolean isPrimary,
                        Instant createdAt) {}
}
