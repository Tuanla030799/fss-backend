package com.fss.backend.content.feedback;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Collection {@code customer_feedbacks}: photos of customer feedback (chat screenshots, customers wearing the shoes)
 * shown on the home page. Usually only the image is set; {@code customerName} and {@code description} are optional.
 */
@Document(CustomerFeedbackDocument.COLLECTION)
public record CustomerFeedbackDocument(@Id UUID id, UUID fileId, String customerName, String description, int sortOrder,
                                       String status, UUID createdBy, UUID updatedBy, Instant createdAt,
                                       Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "customer_feedbacks";
}
