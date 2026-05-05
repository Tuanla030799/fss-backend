package com.fss.backend.catalog.product;

import java.util.UUID;

public record ProductVariant(UUID id, UUID productId, String name, UUID colorId, String colorName, String colorCode,
                             UUID imageFileId, String imageUrl, String status, Integer sortOrder) {}
