package com.fss.backend.catalog.product;

import java.util.UUID;

public record ProductImage(UUID id, UUID productId, UUID fileId, String imageUrl, String altText, String imageType,
                           Integer sortOrder, Boolean isPrimary) {}
