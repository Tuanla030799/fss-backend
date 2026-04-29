package com.fss.backend.catalog.category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Category(UUID id, UUID parentId, String name, String slug, String description, String status,
                       Integer sortOrder, OffsetDateTime createdAt) {}
