package com.fss.backend.content.feedback;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerFeedback(UUID id, UUID fileId, String imageUrl, String customerName, String description,
                               int sortOrder, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
