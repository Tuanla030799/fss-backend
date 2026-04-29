package com.fss.backend.catalog.product;

import java.util.UUID;

public record ProductImageRequest(UUID fileId, String altText, String imageType, Integer sortOrder, Boolean isPrimary) {}
