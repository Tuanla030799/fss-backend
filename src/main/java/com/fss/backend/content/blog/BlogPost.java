package com.fss.backend.content.blog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlogPost(UUID id, String title, String slug, String excerpt, String contentJson, String contentHtml,
                       UUID coverFileId, String coverImageUrl, String status, OffsetDateTime publishedAt,
                       OffsetDateTime createdAt) {}
