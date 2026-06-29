package com.fss.backend.catalog.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSummary(UUID id, UUID categoryId, String categoryName, UUID brandId, String brandName, String brandSlug,
                             String brandSizeGuideUrl,
                             String gender, String name, String slug,
                             String shortDescription, String status, Boolean isFeatured, Integer featuredOrder,
                             BigDecimal minPrice, BigDecimal minSalePrice, Integer totalStock,
                             UUID primaryFileId, String primaryImageUrl, OffsetDateTime createdAt) {}
