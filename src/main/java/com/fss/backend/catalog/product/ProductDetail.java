package com.fss.backend.catalog.product;

import com.fss.backend.catalog.sku.Sku;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductDetail(UUID id, UUID categoryId, String categoryName, UUID brandId, String brandName, String brandSlug,
                            String gender, String name, String slug,
                            String shortDescription, String descriptionJson, String status, Boolean isFeatured,
                            Integer featuredOrder, OffsetDateTime createdAt,
                            List<ProductImage> images, List<ProductVariant> variants, List<Sku> skus) {}
