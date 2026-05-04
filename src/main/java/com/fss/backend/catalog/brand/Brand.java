package com.fss.backend.catalog.brand;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Brand(UUID id, String name, String slug, String description, UUID fileId, String imageUrl,
                    String status, Integer sortOrder, OffsetDateTime createdAt) {}
