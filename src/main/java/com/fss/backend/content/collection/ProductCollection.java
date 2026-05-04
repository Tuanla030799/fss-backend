package com.fss.backend.content.collection;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCollection(UUID id, String name, String slug, String description,
                                UUID fileId, String imageUrl, String status, Integer sortOrder,
                                Integer productCount, OffsetDateTime createdAt) {}
