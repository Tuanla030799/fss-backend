package com.fss.backend.content.faq;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Collection {@code faqs}. {@code htmlFilePaths} lists uploaded images used in {@code answerHtml}. */
@Document(FaqDocument.COLLECTION)
public record FaqDocument(@Id UUID id, String question, String answerHtml, List<String> htmlFilePaths, int sortOrder,
                          String status, UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                          boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "faqs";
}
