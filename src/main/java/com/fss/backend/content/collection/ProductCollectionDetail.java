package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductSummary;
import com.fss.backend.common.PageResult;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCollectionDetail(UUID id, String name, String slug, String excerpt, String descriptionHtml,
                                      UUID fileId, String imageUrl, String status, Integer sortOrder,
                                      Integer productCount, OffsetDateTime createdAt,
                                      PageResult<ProductSummary> products) {}
