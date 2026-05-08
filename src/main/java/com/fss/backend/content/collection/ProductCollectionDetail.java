package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductSummary;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductCollectionDetail(UUID id, String name, String slug, String description, String descriptionHtml,
                                      UUID fileId, String imageUrl, String status, Integer sortOrder,
                                      Integer productCount, OffsetDateTime createdAt,
                                      List<ProductSummary> products) {}
