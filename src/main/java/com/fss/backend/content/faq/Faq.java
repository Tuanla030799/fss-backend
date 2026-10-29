package com.fss.backend.content.faq;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Faq(UUID id, String question, String answerHtml, int sortOrder, String status,
                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
